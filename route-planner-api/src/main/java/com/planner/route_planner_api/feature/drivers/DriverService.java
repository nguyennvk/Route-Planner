package com.planner.route_planner_api.feature.drivers;

import com.planner.route_planner_api.feature.vehicles.VehicleLastPosition;
import com.planner.route_planner_api.feature.vehicles.VehicleLastPositionRepository;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planner.route_planner_api.common.error.ConflictException;
import com.planner.route_planner_api.common.error.ResourceNotFoundException;
import com.planner.route_planner_api.common.time.ZonedTime;
import com.planner.route_planner_api.feature.companies.CompanyAccess;
import com.planner.route_planner_api.feature.drivers.dto.DriverRejectRequest;
import com.planner.route_planner_api.feature.drivers.dto.PositionMessage;
import com.planner.route_planner_api.feature.drivers.dto.PositionRequest;
import com.planner.route_planner_api.feature.drivers.dto.StopFailRequest;
import com.planner.route_planner_api.feature.orders.Order;
import com.planner.route_planner_api.feature.orders.OrderStatus;
import com.planner.route_planner_api.feature.planning.PlanCompletion;
import com.planner.route_planner_api.feature.planning.PlanStatus;
import com.planner.route_planner_api.feature.planning.dto.RouteResponse;
import com.planner.route_planner_api.feature.route.Route;
import com.planner.route_planner_api.feature.route.RouteRepository;
import com.planner.route_planner_api.feature.route.RouteStop;
import com.planner.route_planner_api.feature.route.RouteStopRepository;

// Read-only by default: the transaction keeps lazy relations loadable even with open-in-view off,
// and readOnly lets Hibernate skip dirty checking at the end. Methods that change a route override it
@Service
@Transactional(readOnly = true)
public class DriverService {

    // Drafts are still being edited by the dispatcher; drivers only see plans that were sent out
    private static final List<PlanStatus> VISIBLE_TO_DRIVERS = List.of(PlanStatus.published, PlanStatus.completed);

    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final VehicleLastPositionRepository vehicleLastPositionRepository;
    private final CompanyAccess companyAccess;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final PlanCompletion planCompletion;

    public DriverService(
        RouteRepository routeRepository,
        RouteStopRepository routeStopRepository,
        VehicleLastPositionRepository vehicleLastPositionRepository,
        CompanyAccess companyAccess,
        SimpMessagingTemplate simpMessagingTemplate,
        PlanCompletion planCompletion
    ) {
        this.routeRepository = routeRepository;
        this.routeStopRepository = routeStopRepository;
        this.vehicleLastPositionRepository = vehicleLastPositionRepository;
        this.companyAccess = companyAccess;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.planCompletion = planCompletion;
    }

    // The logged-in driver's routes, earliest first
    public List<RouteResponse> getRoutes(Long userId) {
        // Membership checked once per company, not once per route (a driver usually has one company)
        Map<Long, Boolean> activeIn = new HashMap<>();
        List<Route> routes = routeRepository
            .findByDriverIdAndPlanStatusInOrderByPlannedStartAsc(userId, VISIBLE_TO_DRIVERS).stream()
            .filter(route -> activeIn.computeIfAbsent(route.getPlan().getCompany().getId(),
                companyId -> companyAccess.findActiveMember(companyId, userId).isPresent()))
            .toList();

        // All stops of all routes in one query, then grouped, instead of one query per route
        Map<Route, List<RouteStop>> stopsByRoute = routeStopRepository.findByRouteIn(routes).stream()
            .collect(Collectors.groupingBy(RouteStop::getRoute));

        return routes.stream()
            .map(route -> toResponse(route, stopsByRoute.getOrDefault(route, List.of())))
            .toList();
    }

    public RouteResponse getRouteById(Long userId, Long routeId) {
        return toResponse(findOwnRoute(userId, routeId));
    }

    // Public link, no login: the token itself is the permission
    public RouteResponse getRouteByToken(String token) {
        return toResponse(findSharedRoute(token));
    }

    // The class default is readOnly, where Hibernate never flushes: without this the change would stay in memory.
    // The @Version column turns two answers racing each other into a 409 instead of the last one winning
    @Transactional
    public RouteResponse acceptRoute(Long userId, Long routeId) {
        Route route = findOwnRoute(userId, routeId);
        route.accept();
        return toResponse(route);
    }

    @Transactional
    public RouteResponse rejectRoute(Long userId, Long routeId, DriverRejectRequest request) {
        Route route = findOwnRoute(userId, routeId);
        route.reject(request.reason());
        return toResponse(route);
    }

    // The share link answers for the route's driver, the same way the logged-in endpoints do
    @Transactional
    public RouteResponse acceptRouteByToken(String token) {
        Route route = findSharedRoute(token);
        route.accept();
        return toResponse(route);
    }

    @Transactional
    public RouteResponse rejectRouteByToken(String token, DriverRejectRequest request) {
        Route route = findSharedRoute(token);
        route.reject(request.reason());
        return toResponse(route);
    }

    @Transactional
    public RouteResponse startRoute(Long userId, Long routeId) {
        return start(findOwnRoute(userId, routeId));
    }

    @Transactional
    public RouteResponse startRouteByToken(String token) {
        return start(findSharedRoute(token));
    }

    @Transactional
    public RouteResponse finishRoute(Long userId, Long routeId) {
        return finish(findOwnRoute(userId, routeId));
    }

    @Transactional
    public RouteResponse finishRouteByToken(String token) {
        return finish(findSharedRoute(token));
    }

    // The driver's answer for one stop. Each returns the whole route, so the app shows the new state at once
    @Transactional
    public RouteResponse arriveStop(Long userId, Long routeId, Long stopId) {
        return arrive(findOwnRoute(userId, routeId), stopId);
    }

    @Transactional
    public RouteResponse deliverStop(Long userId, Long routeId, Long stopId) {
        return deliver(findOwnRoute(userId, routeId), stopId);
    }

    @Transactional
    public RouteResponse failStop(Long userId, Long routeId, Long stopId, StopFailRequest request) {
        return fail(findOwnRoute(userId, routeId), stopId, request.reason());
    }

    @Transactional
    public RouteResponse arriveStopByToken(String token, Long stopId) {
        return arrive(findSharedRoute(token), stopId);
    }

    @Transactional
    public RouteResponse deliverStopByToken(String token, Long stopId) {
        return deliver(findSharedRoute(token), stopId);
    }

    @Transactional
    public RouteResponse failStopByToken(String token, Long stopId, StopFailRequest request) {
        return fail(findSharedRoute(token), stopId, request.reason());
    }

    @Transactional
    public void postDriverPosition(Long userId, Long routeId, PositionRequest request) {
        Route route = findOwnRoute(userId, routeId);
        route.requireInProgress();
        Optional<VehicleLastPosition> existing = vehicleLastPositionRepository.findById(route.getVehicle().getId());
        VehicleLastPosition position;
        if (existing.isPresent()) {
            position = existing.get();
            position.moveTo(route, request.latitude(), request.longitude(), request.speedKmh());
        } else {
            position = vehicleLastPositionRepository.save(
                new VehicleLastPosition(route, request.latitude(), request.longitude(), request.speedKmh()));
        }
        ZoneId zone = route.getPlan().getCompany().zoneId();

        // Broadcast to the subscribers
        simpMessagingTemplate.convertAndSend(
            "/topic/routes/" + routeId + "/position",
            new PositionMessage(
                routeId, 
                position.getLatitude(), 
                position.getLongitude(), 
                position.getSpeedKmh(),
                ZonedTime.toLocalDateTime(position.getRecordedAt(), zone)
            )
        );
    }

    // One driver drives one van at a time, and a van is on one route at a time. A second open route is almost
    // always one somebody forgot to finish; it would also never get positions (the phone tracks one route)
    // and its orders would stay out_for_delivery. The message names the route, so the driver can finish it.
    private void requireNoOtherActiveRoute(Route route) {
        if (route.getDriver() != null) {
            routeRepository.findFirstByDriverIdAndIdNotAndStartedAtIsNotNullAndFinishedAtIsNull(route.getDriver().getId(), route.getId())
                .ifPresent(other -> {
                    throw new ConflictException("Finish your route for " + other.getPlan().getPlanDate()
                        + " (" + other.getVehicle().getName() + ") before starting this one");
                });
        }
        routeRepository.findFirstByVehicleIdAndIdNotAndStartedAtIsNotNullAndFinishedAtIsNull(route.getVehicle().getId(), route.getId())
            .ifPresent(other -> {
                throw new ConflictException(other.getVehicle().getName() + " is still on a route in progress for "
                    + other.getPlan().getPlanDate() + "; that route has to finish first");
            });
    }

    // Orders cancelled after publishing stay cancelled; the rest are now on the road
    private RouteResponse start(Route route) {
        // Before route.start(): Hibernate flushes pending changes before running a query, so checking after it
        // would flush this route as started, hit V5's unique index and give a vague 409 instead of these messages
        requireNoOtherActiveRoute(route);
        List<RouteStop> stops = routeStopRepository.findByRoute(route);
        route.start();
        stops.stream()
            .map(RouteStop::getOrder)
            .filter(order -> order.getStatus() == OrderStatus.planned)
            .forEach(Order::markOutForDelivery);
        return toResponse(route, stops);
    }

    // Every stop needs an answer first, so a customer never reads "not delivered" for a parcel that arrived
    // but wasn't tapped. Stops whose order was cancelled after publishing are skipped: nobody drives there.
    // The last route of a plan to finish completes the plan.
    private RouteResponse finish(Route route) {
        List<RouteStop> stops = routeStopRepository.findByRoute(route);
        stops.stream()
            .filter(stop -> stop.isOpen() && stop.getOrder().getStatus() == OrderStatus.cancelled)
            .forEach(RouteStop::skip);
        List<Integer> open = stops.stream()
            .filter(RouteStop::isOpen)
            .map(stop -> stop.getSequence() + 1)
            .sorted()
            .toList();
        if (!open.isEmpty()) {
            throw new ConflictException("Mark every stop as delivered or not delivered before finishing. Still open: stop "
                + open.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        }
        route.finish();
        planCompletion.completeIfDone(route.getPlan());
        return toResponse(route, stops);
    }

    private RouteResponse arrive(Route route, Long stopId) {
        List<RouteStop> stops = routeStopRepository.findByRoute(route);
        findStop(route, stops, stopId).arrive();
        return toResponse(route, stops);
    }

    private RouteResponse deliver(Route route, Long stopId) {
        List<RouteStop> stops = routeStopRepository.findByRoute(route);
        RouteStop stop = findStop(route, stops, stopId);
        requireNotCancelled(stop);
        stop.deliver(null);
        stop.getOrder().markDelivered();
        return toResponse(route, stops);
    }

    private RouteResponse fail(Route route, Long stopId, String reason) {
        List<RouteStop> stops = routeStopRepository.findByRoute(route);
        RouteStop stop = findStop(route, stops, stopId);
        requireNotCancelled(stop);
        stop.fail(reason.trim());
        stop.getOrder().markFailed();
        return toResponse(route, stops);
    }

    // Only on a route being driven, and only a stop of this route: another route's stop id looks like "not found"
    private static RouteStop findStop(Route route, List<RouteStop> stops, Long stopId) {
        route.requireInProgress();
        return stops.stream()
            .filter(stop -> stop.getId().equals(stopId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Stop", stopId));
    }

    private static void requireNotCancelled(RouteStop stop) {
        if (stop.getOrder().getStatus() == OrderStatus.cancelled) {
            throw new ConflictException("This order was cancelled, so there's nothing to deliver");
        }
    }

    // Someone else's route, an unpublished one, or a company the driver left all look like "not found",
    // so a driver can't probe which route ids exist
    private Route findOwnRoute(Long userId, Long routeId) {
        return routeRepository.findByIdAndDriverId(routeId, userId)
            .filter(this::isVisible)
            .filter(r -> isActiveMember(r, userId))
            .orElseThrow(() -> new ResourceNotFoundException("Route", routeId));
    }

    private Route findSharedRoute(String token) {
        return routeRepository.findByShareToken(token)
            .filter(this::isVisible)
            .orElseThrow(() -> new ResourceNotFoundException("Route", "token"));   // never echo the token
    }

    private boolean isVisible(Route route) {
        return VISIBLE_TO_DRIVERS.contains(route.getPlan().getStatus());
    }

    // A driver removed from the company keeps driver_user_id on old routes; they shouldn't see them anymore
    private boolean isActiveMember(Route route, Long userId) {
        return companyAccess.findActiveMember(route.getPlan().getCompany().getId(), userId).isPresent();
    }

    private RouteResponse toResponse(Route route) {
        return toResponse(route, routeStopRepository.findByRoute(route));
    }

    private static RouteResponse toResponse(Route route, List<RouteStop> stops) {
        return RouteResponse.toRoute(route, stops, route.getPlan().getCompany().zoneId());
    }
}
