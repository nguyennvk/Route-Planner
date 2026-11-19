package com.planner.route_planner_api.feature.planning;

import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planner.route_planner_api.common.error.BadRequestException;
import com.planner.route_planner_api.common.error.ConflictException;
import com.planner.route_planner_api.common.error.ForbiddenException;
import com.planner.route_planner_api.common.error.ResourceNotFoundException;
import com.planner.route_planner_api.common.error.ServiceUnavailableException;
import com.planner.route_planner_api.common.time.ZonedTime;
import com.planner.route_planner_api.common.web.PageResponse;
import com.planner.route_planner_api.feature.addresses.Address;
import com.planner.route_planner_api.feature.addresses.AddressRepository;
import com.planner.route_planner_api.feature.auth.User;
import com.planner.route_planner_api.feature.companies.Company;
import com.planner.route_planner_api.feature.companies.CompanyAccess;
import com.planner.route_planner_api.feature.companies.CompanyMember;
import com.planner.route_planner_api.feature.companies.MemberRole;
import com.planner.route_planner_api.feature.orders.Order;
import com.planner.route_planner_api.feature.orders.OrderRepository;
import com.planner.route_planner_api.feature.orders.OrderStatus;
import com.planner.route_planner_api.feature.planning.constraints.PlanPreference;
import com.planner.route_planner_api.feature.planning.constraints.PlanningWeights;
import com.planner.route_planner_api.feature.planning.dto.PlanPublishRequest;
import com.planner.route_planner_api.feature.planning.dto.PlanRequest;
import com.planner.route_planner_api.feature.planning.dto.PlanResponse;
import com.planner.route_planner_api.feature.planning.dto.PlanSummaryResponse;
import com.planner.route_planner_api.feature.planning.geo_provider.OsrmAPI;
import com.planner.route_planner_api.feature.planning.geo_provider.OsrmTableResponse;
import com.planner.route_planner_api.feature.planning.optimizer.Location;
import com.planner.route_planner_api.feature.planning.optimizer.PlannedVehicle;
import com.planner.route_planner_api.feature.planning.optimizer.VehicleRoutePlan;
import com.planner.route_planner_api.feature.planning.optimizer.Visit;
import com.planner.route_planner_api.feature.route.Route;
import com.planner.route_planner_api.feature.route.RouteRepository;
import com.planner.route_planner_api.feature.route.RouteStop;
import com.planner.route_planner_api.feature.route.RouteStopRepository;
import com.planner.route_planner_api.feature.route.StopStatus;
import com.planner.route_planner_api.feature.vehicles.Vehicle;
import com.planner.route_planner_api.feature.vehicles.VehicleLastPositionRepository;
import com.planner.route_planner_api.feature.vehicles.VehicleRepository;
import com.planner.route_planner_api.feature.vehicles.VehicleShift;
import com.planner.route_planner_api.feature.vehicles.VehicleShiftRepository;
import com.planner.route_planner_api.feature.vehicles.vehicle.dto.VehiclePositionResponse;

import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverManager;

@Service
public class PlanningService {

    // Created by the Timefold starter from the @PlanningSolution class; just ask for it here
    private final SolverManager<VehicleRoutePlan> solverManager;
    private final PlanWriter planWriter;
    private final PlanCompletion planCompletion;
    private final PlanRepository planRepository;
    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final CompanyAccess companyAccess;
    private final VehicleShiftRepository vehicleShiftRepository;
    private final OrderRepository orderRepository;
    private final OsrmAPI osrmAPI;
    private final VehicleLastPositionRepository vehicleLastPositionRepository;

    private static final Logger log = LoggerFactory.getLogger(PlanningService.class);

    // Stand-ins when OSRM can't route between two points: a day of driving and 1,000 km. Big enough that
    // the solver avoids the leg, small enough that adding a few never overflows a long or LocalDateTime
    private static final long UNREACHABLE_SECONDS = 24 * 3600;
    private static final long UNREACHABLE_METERS = 1_000_000;


    public PlanningService(
        SolverManager<VehicleRoutePlan> solverManager,
        AddressRepository addressRepository,
        VehicleRepository vehicleRepository,
        PlanWriter planWriter,
        PlanCompletion planCompletion,
        PlanRepository planRepository,
        RouteRepository routeRepository,
        RouteStopRepository routeStopRepository,
        CompanyAccess companyAccess,
        VehicleShiftRepository vehicleShiftRepository,
        OrderRepository orderRepository,
        OsrmAPI osrmAPI,
        VehicleLastPositionRepository vehicleLastPositionRepository
    ) {
        this.vehicleLastPositionRepository = vehicleLastPositionRepository;
        this.solverManager = solverManager;
        this.planWriter = planWriter;
        this.planCompletion = planCompletion;
        this.planRepository = planRepository;
        this.routeRepository = routeRepository;
        this.routeStopRepository = routeStopRepository;
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
        Company company = companyMember.getCompany();
        // Get invalid orderIds
        List<Long> invalidOrderIds = extractInvalidIds(orders, request.orderIds(), Order::getId);

        // Retrieve data from request
        List<VehicleShift> shifts = vehicleShiftRepository.findByVehicleCompanyIdAndIdIn(companyId, request.shiftIds());
        requireShiftsOnSameDate(shifts, request.planDate(), ZoneId.of(company.getTimezone()));
        requireShiftsFree(shifts);
        Map<Long, VehicleShift> shiftsById = shifts.stream().collect(Collectors.toMap(VehicleShift::getId, Function.identity()));

        // Get invalid shiftIds
        List<Long> invalidShiftIds = extractInvalidIds(shifts, request.shiftIds(), VehicleShift::getId);

        // The solver works in the company's wall-clock time; convert back to Instant when saving
        ZoneId zone = company.zoneId();

        List<PlannedVehicle> plannedVehicles = getPlannedVehicles(shifts, zone);
        LocalDateTime[] timeBound = getTimeBound(plannedVehicles);
        List<Visit> visits = getVisits(orders, zone);

        // Before the plan row exists, so a bad request is a 400 and leaves nothing stuck in 'solving'
        CostSettings costSettings = parseCostSettings(request.planPreferences(), shifts);

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
            problem.setCostSettings(costSettings);
            problem.setConstraintWeightOverrides(PlanningWeights.from(request.planScorePreference()));

            SolverJob<VehicleRoutePlan> job = solverManager.solve(UUID.randomUUID(), problem);
            // Use the returned plan: Timefold solves a copy, so `problem` itself stays unsolved
            VehicleRoutePlan solved = job.getFinalBestSolution();
            drawMapPolyline(solved.getVehicles());
            PlanWriter.SavedSolution saved = planWriter.saveSolution(plan.getId(), solved, shiftsById, ordersById, zone);

            // Only the solver knows which orders it left out; they aren't stored (they simply stay 'pending')
            List<Long> unassignedOrderIds = solved.getVisits().stream()
                .filter(visit -> visit.getVehicle() == null)
                .map(Visit::getOrderId)
                .toList();
            return PlanResponse.from(saved.plan(), saved.routes(), saved.stops(), unassignedOrderIds);
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

    // Short and DB-only, so a transaction around the whole method is fine. It works here (unlike on a
    // private method) because the controller calls it from outside, through Spring's proxy
    // Snapshot for the planner's map: where each van on a route in progress was last seen.
    // Live updates after this come over /topic/routes/{routeId}/position. Owners and dispatchers only, like that topic.
    @Transactional(readOnly = true)
    public List<VehiclePositionResponse> activePositions(Long userId, Long planId) {
        Plan plan = planRepository.findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
        CompanyMember member = companyAccess.findActiveMember(plan.getCompany().getId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
        if (member.getRole() == MemberRole.driver) {
            throw new ForbiddenException("Only owners and dispatchers can see vehicle positions");
        }
        ZoneId zone = plan.getCompany().zoneId();
        return vehicleLastPositionRepository.findActiveByPlanId(planId).stream()
            .map(position -> VehiclePositionResponse.from(position, zone))
            .toList();
    }

    // The plan history, newest first. Every filter is optional: one exact day (the Plan page), a range of
    // days from / to (both inclusive), and a status. Owners and dispatchers only
    @Transactional(readOnly = true)
    public PageResponse<PlanSummaryResponse> listPlans(Long userId, Long companyId, LocalDate date, LocalDate from,
                                                       LocalDate to, PlanStatus status, int page, int size) {
        CompanyMember member = companyAccess.requireMember(companyId, userId);
        requirePlanner(member);
        if (from != null && to != null && to.isBefore(from)) {
            throw new BadRequestException("The end of the date range must be on or after its start");
        }
        ZoneId zone = member.getCompany().zoneId();

        // Build the WHERE clause from only the filters the client sent
        Specification<Plan> filters = (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.equal(root.get("company").get("id"), companyId));
            if (date != null) conditions.add(cb.equal(root.get("planDate"), date));
            if (from != null) conditions.add(cb.greaterThanOrEqualTo(root.get("planDate"), from));
            if (to != null) conditions.add(cb.lessThanOrEqualTo(root.get("planDate"), to));
            if (status != null) conditions.add(cb.equal(root.get("status"), status));
            return cb.and(conditions.toArray(Predicate[]::new));
        };
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Order.desc("planDate"), Sort.Order.desc("id")));
        Page<Plan> plans = planRepository.findAll(filters, pageRequest);

        Map<Long, RouteRepository.PlanCounts> counts = routeRepository
            .countByPlanIds(plans.map(Plan::getId).getContent()).stream()
            .collect(Collectors.toMap(RouteRepository.PlanCounts::getPlanId, Function.identity()));

        return PageResponse.from(plans.map(plan -> {
            RouteRepository.PlanCounts count = counts.get(plan.getId());   // null: the plan has no routes (failed, or nothing fit)
            return PlanSummaryResponse.from(plan, zone, count == null ? 0 : count.getRoutes(), count == null ? 0 : count.getStops());
        }));
    }

    // One saved plan with its routes and stops. Which orders were left out isn't stored, so unassignedOrderIds is empty
    @Transactional(readOnly = true)
    public PlanResponse getPlan(Long userId, Long planId) {
        Plan plan = planRepository.findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
        // Not a member of the plan's company -> same 404 as an unknown id, so ids from other companies don't leak
        CompanyMember member = companyAccess.findActiveMember(plan.getCompany().getId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
        requirePlanner(member);

        List<Route> routes = routeRepository.findByPlan(plan);
        List<RouteStop> stops = routes.isEmpty() ? List.of() : routeStopRepository.findByRouteIn(routes);
        return PlanResponse.from(plan, routes, stops, List.of());
    }

    // Early answer for a shift that's already out on a published plan: that plan could never be published
    // (claim() refuses at publish), so don't spend a solve on it. The check that guarantees it is at publish
    private static void requireShiftsFree(List<VehicleShift> shifts) {
        List<String> taken = shifts.stream()
            .filter(VehicleShift::isPublished)
            .map(shift -> shift.getVehicle().getName())
            .toList();
        if (!taken.isEmpty()) {
            throw new ConflictException("Already on a published plan: " + String.join(", ", taken)
                + ". Leave these vehicles out, or release their route first");
        }
    }

    private static void requireShiftsOnSameDate(List<VehicleShift> shifts, LocalDate date, ZoneId zone) {
        Instant dayStart = ZonedTime.startOfDay(date, zone);
        Instant dayEnd = ZonedTime.startOfDay(date.plusDays(1), zone);
        List<Long> otherDays = shifts.stream()
            .filter(shift -> !(shift.getStartAt().isBefore(dayEnd) && shift.getEndAt().isAfter(dayStart)))
            .map(VehicleShift::getId)
            .toList();
        if (!otherDays.isEmpty()) {
            throw new BadRequestException("These shifts aren't on " + date + ": " + otherDays);
        }
    }

    // Drivers see their own routes on My routes, not whole plans
    private static void requirePlanner(CompanyMember member) {
        if (member.getRole() == MemberRole.driver) {
            throw new ForbiddenException("Only owners and dispatchers can see plans");
        }
    }

    @Transactional
    public PlanResponse publishPlan(Long userId, Long planId, PlanPublishRequest request) {
        Plan plan = planRepository.findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
        // Not a member of the plan's company -> same 404 as an unknown id, so ids from other companies don't leak
        CompanyMember member = companyAccess.findActiveMember(plan.getCompany().getId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
        companyAccess.requireOwner(member);

        List<Route> routes = routeRepository.findByPlan(plan);
        List<Long> routesWithoutDriver = routes.stream()
            .filter(route -> route.getDriver() == null)
            .map(Route::getId)
            .toList();
        if (!routesWithoutDriver.isEmpty()) {
            throw new ConflictException("Assign a driver before publishing; routes without one: " + routesWithoutDriver);
        }

        List<RouteStop> stops = routeStopRepository.findByRouteIn(routes);
        stops.forEach(stop -> stop.getOrder().markPlanned());   // throws if an order was cancelled or planned elsewhere
        // Same for vans and drivers: each shift can be out on one published route only. claim() throws if
        // another plan already has it; @Version on the shift catches two publishes racing for it
        routes.forEach(route -> route.getVehicleShift().claim(route));
        plan.publish();

        // TODO request.notifyDrivers(): no notification channel yet (push / email / Kafka)
        return PlanResponse.from(plan, routes, stops, List.of());
    }

    // The owner's answer to a turned-down route: close it for good and put its orders back on the planning list.
    // All in one transaction: the driver can't take the route back (Route.release), its stops are skipped so no
    // lookup treats them as live, and the orders are pending again so the next solve can pick them up.
    // Stops already skipped (their order was cancelled) are left alone. @Version on Route makes a driver accepting
    // at the same moment a 409 for one of the two instead of a half-applied mix
    @Transactional
    public PlanResponse releaseRoute(Long userId, Long planId, Long routeId) {
        Route route = routeRepository.findById(routeId)
            .filter(r -> r.getPlan().getId().equals(planId))      // a route of another plan: as if it didn't exist
            .orElseThrow(() -> new ResourceNotFoundException("Route", routeId));
        Plan plan = route.getPlan();
        // Not a member of the route's company -> same 404 as an unknown id, so ids from other companies don't leak
        CompanyMember member = companyAccess.findActiveMember(plan.getCompany().getId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Route", routeId));
        companyAccess.requireOwner(member);

        route.release();
        route.getVehicleShift().free();     // the van and driver can go into the next plan
        routeStopRepository.findByRoute(route).stream()
            .filter(stop -> stop.getStatus() == StopStatus.pending)
            .forEach(stop -> {
                stop.skip();
                stop.getOrder().release();
            });
        planCompletion.completeIfDone(plan);     // this may have been the last route the plan was waiting for

        List<Route> routes = routeRepository.findByPlan(plan);
        return PlanResponse.from(plan, routes, routeStopRepository.findByRouteIn(routes), List.of());
    }

    @Transactional 
    public void deletePlan(Long userId, Long planId) {
        Plan plan = planRepository.findById(planId).orElseThrow(
            () -> new ResourceNotFoundException("Plan", planId)
        );

        Company company = plan.getCompany();
        CompanyMember member = companyAccess.findActiveMember(company.getId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
        companyAccess.requireOwner(member);

        if (plan.getStatus() != PlanStatus.draft && plan.getStatus() != PlanStatus.failed) {
            throw new ConflictException("Only a draft or failed plan can be deleted; this one is " + plan.getStatus());
        }
        planRepository.delete(plan);        
    }

    // Every shift needs prices: a missing one would cost $0, and the solver would load everything onto it
    private static CostSettings parseCostSettings(Map<Long, PlanPreference> planPreferences, List<VehicleShift> shifts) {
        List<Long> withoutPrices = shifts.stream()
            .map(VehicleShift::getId)
            .filter(shiftId -> !planPreferences.containsKey(shiftId))
            .toList();
        if (!withoutPrices.isEmpty()) {
            throw new BadRequestException("Missing prices for shifts " + withoutPrices);
        }

        Map<Long, Long> fuelCentsPerKm = new HashMap<>();
        Map<Long, Long> wageCentsPerHour = new HashMap<>();
        Map<Long, Long> overtimeCentsPerHour = new HashMap<>();
        for (VehicleShift shift : shifts) {
            PlanPreference prices = planPreferences.get(shift.getId());
            fuelCentsPerKm.put(shift.getId(), prices.fuelCentsPerKm());
            wageCentsPerHour.put(shift.getId(), prices.wageCentsPerHour());
            overtimeCentsPerHour.put(shift.getId(), prices.overtimeCentsPerHour());
        }
        return new CostSettings(fuelCentsPerKm, wageCentsPerHour, overtimeCentsPerHour);
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
                locatioDurationnMap.put(locations.get(j), seconds == null ? UNREACHABLE_SECONDS : Math.round(seconds));
                locationDistanceMap.put(locations.get(j), meters == null ? UNREACHABLE_METERS : Math.round(meters));
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
