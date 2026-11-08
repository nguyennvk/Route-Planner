package com.planner.route_planner_api.feature.planning;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planner.route_planner_api.common.time.ZonedTime;
import com.planner.route_planner_api.feature.auth.User;
import com.planner.route_planner_api.feature.companies.Company;
import com.planner.route_planner_api.feature.orders.Order;
import com.planner.route_planner_api.feature.planning.optimizer.PlannedVehicle;
import com.planner.route_planner_api.feature.planning.optimizer.VehicleRoutePlan;
import com.planner.route_planner_api.feature.planning.optimizer.Visit;
import com.planner.route_planner_api.feature.route.Route;
import com.planner.route_planner_api.feature.route.RouteRepository;
import com.planner.route_planner_api.feature.route.RouteStop;
import com.planner.route_planner_api.feature.route.RouteStopRepository;
import com.planner.route_planner_api.feature.vehicles.VehicleShift;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;

// All plan writes live here so calls from PlanningService go through Spring's proxy and get a real
// transaction. Kept short on purpose: no OSRM or solver calls in here, so no transaction stays open while solving
@Service
public class PlanWriter {

    private final PlanRepository planRepository;
    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;

    public PlanWriter(PlanRepository planRepository, RouteRepository routeRepository,
                      RouteStopRepository routeStopRepository) {
        this.planRepository = planRepository;
        this.routeRepository = routeRepository;
        this.routeStopRepository = routeStopRepository;
    }

    @Transactional
    public Plan createSolvingPlan(Company company, LocalDate planDate, User createdBy) {
        Plan plan = new Plan(company, planDate, createdBy);
        plan.startSolving();                  // before save, so the INSERT already says 'solving'
        return planRepository.save(plan);
    }

    // Score + routes + stops in one transaction: either the plan is solved with everything, or nothing changed
    @Transactional
    public void saveSolution(Long planId, VehicleRoutePlan solved, Map<Long, VehicleShift> shiftsById,
                             Map<Long, Order> ordersById, ZoneId zone) {
        Plan plan = planRepository.findById(planId).orElseThrow();
        HardMediumSoftScore score = solved.getScore();
        plan.markSolved(score.hardScore(), score.mediumScore(), score.softScore());   // written by dirty checking at commit

        List<Route> routes = new ArrayList<>();
        List<RouteStop> stops = new ArrayList<>();
        for (PlannedVehicle vehicle : solved.getVehicles()) {
            if (vehicle.getVisits().isEmpty()) continue;     // shift got no orders; no route to drive
            VehicleShift shift = shiftsById.get(vehicle.getShiftId());
            Route route = new Route(
                plan,
                shift.getVehicle(),
                shift,
                shift.getDriver(),
                vehicle.getTotalMinutes(),
                vehicle.getTotalDistance(),
                ZonedTime.toInstant(vehicle.getDepartureTime(), zone),
                ZonedTime.toInstant(vehicle.getReturnTime(), zone)
            );
            routes.add(route);

            List<Visit> visits = vehicle.getVisits();       // already in driving order
            for (int i = 0; i < visits.size(); i++) {
                Visit visit = visits.get(i);
                stops.add(new RouteStop(
                    route,
                    ordersById.get(visit.getOrderId()),
                    i,
                    ZonedTime.toInstant(visit.getArrivalTime(), zone),
                    ZonedTime.toInstant(visit.getDepartureTime(), zone)
                ));
            }
        }
        routeRepository.saveAll(routes);                     // routes first: stops need their ids
        routeStopRepository.saveAll(stops);
    }

    // Own transaction, so it still commits when saveSolution rolled back
    @Transactional
    public void markFailed(Long planId) {
        planRepository.findById(planId).ifPresent(Plan::markFailed);
    }
}
