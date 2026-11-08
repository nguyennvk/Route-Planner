package com.planner.route_planner_api.feature.planning;

import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import com.planner.route_planner_api.common.error.BadRequestException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.planner.route_planner_api.common.error.ServiceUnavailableException;
import com.planner.route_planner_api.common.time.ZonedTime;
import com.planner.route_planner_api.feature.addresses.Address;
import com.planner.route_planner_api.feature.addresses.AddressRepository;
import com.planner.route_planner_api.feature.auth.User;
import com.planner.route_planner_api.feature.companies.Company;
import com.planner.route_planner_api.feature.companies.CompanyAccess;
import com.planner.route_planner_api.feature.companies.CompanyMember;
import com.planner.route_planner_api.feature.orders.Order;
import com.planner.route_planner_api.feature.orders.OrderRepository;
import com.planner.route_planner_api.feature.orders.OrderStatus;
import com.planner.route_planner_api.feature.planning.dto.PlanRequest;
import com.planner.route_planner_api.feature.planning.dto.PlanResponse;
import com.planner.route_planner_api.feature.planning.geo_provider.OsrmAPI;
import com.planner.route_planner_api.feature.planning.geo_provider.OsrmTableResponse;
import com.planner.route_planner_api.feature.planning.optimizer.Location;
import com.planner.route_planner_api.feature.planning.optimizer.PlannedVehicle;
import com.planner.route_planner_api.feature.planning.optimizer.VehicleRoutePlan;
import com.planner.route_planner_api.feature.planning.optimizer.Visit;
import com.planner.route_planner_api.feature.vehicles.Vehicle;
import com.planner.route_planner_api.feature.vehicles.VehicleRepository;
import com.planner.route_planner_api.feature.vehicles.VehicleShift;
import com.planner.route_planner_api.feature.vehicles.VehicleShiftRepository;

import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverManager;

@Service
public class PlanningService {

    // Created by the Timefold starter from the @PlanningSolution class; just ask for it here
    private final SolverManager<VehicleRoutePlan> solverManager;
    private final VehicleRepository vehicleRepository;
    private final AddressRepository addressRepository;
    private final PlanWriter planWriter;
    private final CompanyAccess companyAccess;
    private final VehicleShiftRepository vehicleShiftRepository;
    private final OrderRepository orderRepository;
    private final OsrmAPI osrmAPI;

    private static final Logger log = LoggerFactory.getLogger(PlanningService.class);


    public PlanningService(
        SolverManager<VehicleRoutePlan> solverManager,
        AddressRepository addressRepository,
        VehicleRepository vehicleRepository,
        PlanWriter planWriter,
        CompanyAccess companyAccess,
        VehicleShiftRepository vehicleShiftRepository,
        OrderRepository orderRepository,
        OsrmAPI osrmAPI
    ) {
        this.solverManager = solverManager;
        this.addressRepository = addressRepository;
        this.vehicleRepository = vehicleRepository;
        this.planWriter = planWriter;
        this.companyAccess = companyAccess;
        this.vehicleShiftRepository = vehicleShiftRepository;
        this.orderRepository = orderRepository;
        this.osrmAPI = osrmAPI;
    }


    // No @Transactional here: this calls OSRM and waits for the solver. Writes go through PlanWriter
    public PlanResponse solve(
        Long userId,
        Long companyId,
        PlanRequest request
    ) {
        CompanyMember companyMember = companyAccess.requireMember(companyId, userId);
        companyAccess.requireOwner(companyMember);
        List<Order> orders = orderRepository.findByCompanyIdAndStatusAndIdIn(companyId, OrderStatus.pending, request.orderIds());
        Map<Long, Order> ordersById = orders.stream().collect(Collectors.toMap(Order::getId, Function.identity()));
        // Get invalid orderIds
        List<Long> invalidOrderIds = extractInvalidIds(orders, request.orderIds(), Order::getId);

        // Retrieve data from request
        List<VehicleShift> shifts = vehicleShiftRepository.findByVehicleCompanyIdAndIdIn(companyId, request.shiftIds());
        Map<Long, VehicleShift> shiftsById = shifts.stream().collect(Collectors.toMap(VehicleShift::getId, Function.identity()));

        // Get invalid shiftIds
        List<Long> invalidShiftIds = extractInvalidIds(shifts, request.shiftIds(), VehicleShift::getId);

        // The solver works in the company's wall-clock time; convert back to Instant when saving
        Company company = companyMember.getCompany();
        ZoneId zone = company.zoneId();

        List<PlannedVehicle> plannedVehicles = getPlannedVehicles(shifts, zone);
        LocalDateTime[] timeBound = getTimeBound(plannedVehicles);
        List<Visit> visits = getVisits(orders, zone);

        // Create the plan and mark as solving; commits right away so others can see it
        User createdBy = companyMember.getUser();
        Plan plan = planWriter.createSolvingPlan(company, request.planDate(), createdBy);

        // From here on the plan row exists: any failure must move it to 'failed', not leave it 'solving'
        try {
            // Create a distance table using OSRM
            List<Location> locationList = getLocationLists(plannedVehicles, visits);
            Double[][] longLatList = getLongLatLists(locationList);
            OsrmTableResponse distanceTable = osrmAPI.generateDistanceTable(longLatList[0], longLatList[1]);
            populateDistanceTable(locationList, distanceTable);

            VehicleRoutePlan problem = new VehicleRoutePlan(
                "Route plan for " + companyId + "on" + LocalDate.now().toString(),
                timeBound[0],
                timeBound[1],
                plannedVehicles,
                visits
            );

            SolverJob<VehicleRoutePlan> job = solverManager.solve(UUID.randomUUID(), problem);
            // Use the returned plan: Timefold solves a copy, so `problem` itself stays unsolved
            VehicleRoutePlan solved = job.getFinalBestSolution();
            drawMapPolyline(solved.getVehicles());
            planWriter.saveSolution(plan.getId(), solved, shiftsById, ordersById, zone);
            return PlanResponse.from(solved);
        } catch (InterruptedException e) {
            planWriter.markFailed(plan.getId());
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Solving was interrupted", e);
        } catch (ExecutionException e) {
            // Something threw inside the solver, e.g. a constraint or the arrival time calculation
            planWriter.markFailed(plan.getId());
            throw new IllegalStateException("Solving failed", e.getCause());
        } catch (RuntimeException e) {
            // OSRM unavailable, or saving the solution failed (its transaction already rolled back)
            planWriter.markFailed(plan.getId());
            throw e;
        }
    }

    private void drawMapPolyline(List<PlannedVehicle> vehicles){
        for (PlannedVehicle vehicle : vehicles){
            if (vehicle.getVisits().isEmpty()) continue;
            try {
                vehicle.setGeometry(osrmAPI.getRoute(vehicle));
            } catch (ServiceUnavailableException ex) {
                log.warn("No road geometry for shift {}", vehicle.getShiftId(), ex);
            }
            
        }
    }

    private static void populateDistanceTable(List<Location> locations, OsrmTableResponse distanceTableResponse){
        for (int i = 0; i < locations.size(); i++){
            Map<Location, Long> locatioDurationnMap = new HashMap<>();
            Map<Location, Long> locationDistanceMap = new HashMap<>();
            for (int j = 0; j < locations.size(); j++){
                Double seconds = distanceTableResponse.durations()[i][j];
                Double meters = distanceTableResponse.distances()[i][j];
                locatioDurationnMap.put(locations.get(j), seconds == null ? Long.MAX_VALUE / 4 : Math.round(seconds));
                locationDistanceMap.put(locations.get(j), meters == null ? Long.MAX_VALUE / 4 : Math.round(meters));
            }
            locations.get(i).setDrivingTimeInSeconds(locatioDurationnMap);
            locations.get(i).setDrivingDistanceMeter(locationDistanceMap);
        }
    }

    private static Double[][] getLongLatLists(List<Location> locations){
        List<Double> longitudeList = new ArrayList<Double>();
        List<Double> latitudeList = new ArrayList<Double>();
        for (Location loc : locations){
            longitudeList.add(loc.getLongitude());
            latitudeList.add(loc.getLattitude());
        }
        Double[][] result = {longitudeList.toArray(new Double[0]), latitudeList.toArray(new Double[0])};
        return result;

    }
    private static List<Location> getLocationLists(List<PlannedVehicle> plannedVehicle, List<Visit> visits){
        List<Location> locations = new ArrayList<Location>();
        for (PlannedVehicle v : plannedVehicle){
            locations.add(v.getHomeLocation());
        }
        for (Visit v : visits){
            locations.add(v.getLocation());
        }
        return locations;
    }

    private static LocalDateTime[] getTimeBound(List<PlannedVehicle> plannedVehicles){
        LocalDateTime minTime = plannedVehicles.get(0).getDepartureTime();
        LocalDateTime maxTime = plannedVehicles.get(0).getShiftEndTime();
        for (PlannedVehicle v : plannedVehicles){
            minTime = minTime.isBefore(v.getDepartureTime()) ? minTime : v.getDepartureTime();
            maxTime = maxTime.isAfter(v.getShiftEndTime()) ? maxTime : v.getShiftEndTime();
        }
        LocalDateTime[] result = {minTime, maxTime};
        return result;
    }

    private static List<Visit> getVisits(List<Order> orders, ZoneId zone){
        return orders.stream().map(order -> orderToVisit(order, zone)).toList();
    }

    private static Visit orderToVisit(Order order, ZoneId zone){
        Address address = order.getAddress();
        return new Visit(
            order.getId(),
            address.getRawAddress(),
            new Location(address.getLatitude(), address.getLongitude()),
            order.getTotalWeightKg().setScale(0, RoundingMode.CEILING).longValue(),
            ZonedTime.toLocalDateTime(order.getWindowStart(), zone),
            ZonedTime.toLocalDateTime(order.getWindowEnd(), zone),
            order.isWindowStrict(),
            Duration.ofMinutes(order.getServiceMinutes())
        );
    }

    private static <T> List<Long> extractInvalidIds(List<T> foundObject, List<Long> idInput, Function<T, Long> getId){
        Set<Long> validIds = foundObject.stream().map(getId).collect(Collectors.toSet());
        return idInput.stream().filter(id -> !validIds.contains(id)).toList();
    }

    private static List<PlannedVehicle> getPlannedVehicles(List<VehicleShift> shifts, ZoneId zone){
        return shifts.stream().map(shift -> toPlannedVehicle(shift, zone)).toList();
    }

    private static PlannedVehicle toPlannedVehicle(VehicleShift shift, ZoneId zone){
        Vehicle vehicle = shift.getVehicle();
        Address vehicleHomeAddress = vehicle.getHomeDepot().getAddress();
        Location vehicleHomeLocation = new Location(vehicleHomeAddress.getLatitude(), vehicleHomeAddress.getLongitude());
        return new PlannedVehicle(
            shift.getId(),
            vehicle.getId(),
            vehicle.getCapacityKg().intValue(),
            vehicleHomeLocation,
            ZonedTime.toLocalDateTime(shift.getStartAt(), zone),
            ZonedTime.toLocalDateTime(shift.getEndAt(), zone)
        );
    }
}
