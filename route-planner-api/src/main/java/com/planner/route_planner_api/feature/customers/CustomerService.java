package com.planner.route_planner_api.feature.customers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planner.route_planner_api.common.error.ConflictException;
import com.planner.route_planner_api.common.error.ResourceNotFoundException;
import com.planner.route_planner_api.common.error.ServiceUnavailableException;
import com.planner.route_planner_api.common.web.PageResponse;
import com.planner.route_planner_api.feature.addresses.Address;
import com.planner.route_planner_api.feature.addresses.AddressRepository;
import com.planner.route_planner_api.feature.companies.CompanyAccess;
import com.planner.route_planner_api.feature.companies.CompanyMember;
import com.planner.route_planner_api.feature.customers.dto.CustomerRequest;
import com.planner.route_planner_api.feature.customers.dto.CustomerResponse;
import com.planner.route_planner_api.feature.customers.dto.TrackingResponse;
import com.planner.route_planner_api.feature.customers.dto.UpdateCustomerRequest;
import com.planner.route_planner_api.feature.orders.Order;
import com.planner.route_planner_api.feature.orders.OrderRepository;
import com.planner.route_planner_api.feature.orders.OrderStatus;
import com.planner.route_planner_api.feature.planning.PlanStatus;
import com.planner.route_planner_api.feature.planning.geo_provider.OsrmAPI;
import com.planner.route_planner_api.feature.planning.geo_provider.OsrmRouteResponse;
import com.planner.route_planner_api.feature.route.Route;
import com.planner.route_planner_api.feature.route.RouteStop;
import com.planner.route_planner_api.feature.route.RouteStopRepository;
import com.planner.route_planner_api.feature.route.StopStatus;
import com.planner.route_planner_api.feature.vehicles.VehicleLastPosition;
import com.planner.route_planner_api.feature.vehicles.VehicleLastPositionRepository;

import jakarta.persistence.criteria.Predicate;

// Every customer endpoint is owner-only, reads included: customers are personal data (phone, email).
// Same rule as orders, which show the customer inside each order.
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final OrderRepository orderRepository;
    private final RouteStopRepository routeStopRepository;
    private final VehicleLastPositionRepository vehicleLastPositionRepository;
    private final CompanyAccess companyAccess;
    private final OsrmAPI osrmAPI;

    public CustomerService(
            CustomerRepository customerRepository,
            AddressRepository addressRepository,
            OrderRepository orderRepository,
            RouteStopRepository routeStopRepository,
            VehicleLastPositionRepository vehicleLastPositionRepository,
            CompanyAccess companyAccess,
            OsrmAPI osrmAPI
        ){
        this.osrmAPI = osrmAPI;
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.orderRepository = orderRepository;
        this.routeStopRepository = routeStopRepository;
        this.vehicleLastPositionRepository = vehicleLastPositionRepository;
        this.companyAccess = companyAccess;
    }

    @Transactional
    public CustomerResponse createCustomer(Long userId, Long companyId, CustomerRequest request){
        CompanyMember membership = companyAccess.requireMember(companyId, userId);
        companyAccess.requireOwner(membership);

        String email = clean(request.email());
        if (email != null && customerRepository.existsByCompanyIdAndEmail(companyId, email)) {
            throw new ConflictException("Another customer of this company already uses this email");   // 409
        }
        Address defaultAddress = request.defaultAddressId() == null
            ? null
            : findCompanyAddress(companyId, request.defaultAddressId());

        Customer customer = new Customer(membership.getCompany(), request.name().strip(),
            clean(request.phone()), email, defaultAddress);
        return CustomerResponse.from(customerRepository.save(customer));
    }

    // phone: exact match, to find a returning customer when they call.
    //   In a URL, "+" means a space: the client must send it as %2B (?phone=%2B14165550123).
    // search: case-insensitive "contains" on the name
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> listCustomers(
            Long userId, Long companyId, String phone, String search, int page, int size){
        CompanyMember membership = companyAccess.requireMember(companyId, userId);
        companyAccess.requireOwner(membership);

        // Build the WHERE clause from only the filters the client sent
        Specification<Customer> filters = (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.equal(root.get("company").get("id"), companyId));
            if (phone != null && !phone.isBlank()) {
                conditions.add(cb.equal(root.get("phone"), phone.strip()));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.strip().toLowerCase(Locale.ROOT) + "%";
                conditions.add(cb.like(cb.lower(root.get("name")), pattern));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("name", "id"));
        return PageResponse.from(customerRepository.findAll(filters, pageRequest).map(CustomerResponse::from));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(Long userId, Long customerId){
        return CustomerResponse.from(findCustomerForOwner(customerId, userId));
    }

    @Transactional
    public CustomerResponse updateCustomer(Long userId, Long customerId, UpdateCustomerRequest request){
        Customer customer = findCustomerForOwner(customerId, userId);
        Long companyId = customer.getCompany().getId();

        // PATCH: only touch the fields the client sent
        // No save() needed: Hibernate sees the change and runs the UPDATE when the transaction commits
        if (request.name() != null) {
            customer.rename(request.name().strip());
        }
        if (request.phone() != null) {
            customer.changePhone(request.phone().strip());
        }
        if (request.email() != null) {
            String email = clean(request.email());
            if (email != null && customerRepository.existsByCompanyIdAndEmailAndIdNot(companyId, email, customerId)) {
                throw new ConflictException("Another customer of this company already uses this email");
            }
            customer.changeEmail(email);
        }
        if (request.defaultAddressId() != null) {
            customer.changeDefaultAddress(findCompanyAddress(companyId, request.defaultAddressId()));
        }
        return CustomerResponse.from(customer);
    }

    @Transactional(readOnly = true)
    public TrackingResponse trackOrder(String trackToken) {
        Order order = orderRepository.findByTrackingToken(trackToken).orElseThrow(
            () -> new ResourceNotFoundException("Order", trackToken)
        );
        // The stop on the published plan, not a draft's copy (completed: the plan is over but still tracked)
        RouteStop routeStop = routeStopRepository.findForTracking(order.getId(), List.of(PlanStatus.published, PlanStatus.completed))
            .orElseThrow(() -> new ResourceNotFoundException("Order", trackToken));   // not sent to a driver yet
        Route route = routeStop.getRoute();
        int stopsBefore = routeStopRepository.countByRouteIdAndSequenceLessThanAndStatusIn(
            route.getId(), routeStop.getSequence(), List.of(StopStatus.pending, StopStatus.arrived));
        TrackingStage stage = stageOf(routeStop, route, stopsBefore);
        Instant now = Instant.now();

        // The van's last fix, only if it was sent for this route (a van that moved on to another route isn't this one's).
        // No fix yet is normal, not an error: the page just has no van to show
        VehicleLastPosition position = vehicleLastPositionRepository.findById(route.getVehicle().getId())
            .filter(p -> p.getRoute() != null && p.getRoute().getId().equals(route.getId()))
            .orElse(null);

        Eta eta = switch (stage) {
            case next -> nextEta(routeStop, route, position, now);
            case scheduled, on_the_way -> plannedEta(routeStop, route, now);
            case arrived, delivered, failed, not_scheduled -> Eta.NONE;     // at the door or done: no time to predict
        };
        return TrackingResponse.from(routeStop, stage, stopsBefore, eta.start(), eta.end(), eta.delay(), position, eta.geometry());
    }

    // An arrival window, how much later than planned it is, and the van → customer road path when OSRM gave one
    private record Eta(Instant start, Instant end, Duration delay, String geometry) {
        static final Eta NONE = new Eta(null, null, Duration.ZERO, null);
    }

    // Expected arrival against the solver's plan, never negative: being early isn't news for the customer
    private static Duration lateness(RouteStop stop, Instant expected) {
        if (stop.getPlannedArrival() == null) return Duration.ZERO;
        Duration late = Duration.between(stop.getPlannedArrival(), expected);
        return late.isNegative() ? Duration.ZERO : late;
    }

    // Planned arrivals get a wider window than OSRM's: they're hours old and the day can still slip
    private static final Duration PLANNED_EARLY = Duration.ofMinutes(10);
    private static final Duration PLANNED_LATE = Duration.ofMinutes(20);
    private static final Duration PARKING = Duration.ofMinutes(5);

    // Coming to this customer now: OSRM driving time from the van's last fix, widened for traffic and parking.
    // No fix yet, or OSRM down: the planned time is still a good answer, so fall back instead of failing.
    private Eta nextEta(RouteStop stop, Route route, VehicleLastPosition position, Instant now) {
        Address address = stop.getOrder().getAddress();
        if (position != null) {
            try {
                OsrmRouteResponse.Route path = osrmAPI.routeBetween(
                    position.getLatitude(), position.getLongitude(), address.getLatitude(), address.getLongitude());
                if (path.duration() != null) {
                    Duration drive = Duration.ofSeconds(Math.round(path.duration()));
                    // +25 % for traffic OSRM doesn't know about, +5 min to park and reach the door
                    Instant arrival = now.plus(drive);
                    return new Eta(arrival, now.plus(drive.multipliedBy(5).dividedBy(4)).plus(PARKING),
                        lateness(stop, arrival), path.geometry());
                }
            } catch (ServiceUnavailableException e) {
                // logged by OsrmAPI; use the plan below
            }
        }
        return plannedEta(stop, route, now);
    }

    // The solver's planned arrival, moved by however late the route is running, as a window that never starts in the past
    private Eta plannedEta(RouteStop stop, Route route, Instant now) {
        if (stop.getPlannedArrival() == null) return Eta.NONE;      // plans saved before planned times existed
        Instant base = stop.getPlannedArrival().plus(routeDelay(route, now));
        // The delay only counts finished stops, so a van stuck since then still looks on time; the stop isn't
        // done, so it can't arrive before now. Without this the window ends before it starts.
        if (base.isBefore(now)) base = now;
        Instant start = base.minus(PLANNED_EARLY);
        return new Eta(start.isBefore(now) ? now : start, base.plus(PLANNED_LATE), lateness(stop, base), null);
    }

    // How late the route is right now, never negative: an early driver still waits for time windows
    private Duration routeDelay(Route route, Instant now) {
        Duration delay;
        if (route.getStartedAt() == null) {
            // Not out yet: every minute past the planned start pushes every stop back
            delay = route.getPlannedStart() == null ? Duration.ZERO : Duration.between(route.getPlannedStart(), now);
        } else {
            // Out: the most recently finished stop shows how far behind plan the van is. None finished yet: the start
            delay = routeStopRepository.findByRoute(route).stream()
                .filter(s -> s.getActualDeparture() != null && s.getPlannedDeparture() != null)
                .max(Comparator.comparing(RouteStop::getActualDeparture))
                .map(s -> Duration.between(s.getPlannedDeparture(), s.getActualDeparture()))
                .orElseGet(() -> route.getPlannedStart() == null ? Duration.ZERO
                    : Duration.between(route.getPlannedStart(), route.getStartedAt()));
        }
        return delay.isNegative() ? Duration.ZERO : delay;
    }

    private TrackingStage stageOf(RouteStop stop, Route route, long stopsBefore) {
        switch (stop.getStatus()) {
            case delivered: return TrackingStage.delivered;                  // 1. done: success
            case failed:    return TrackingStage.failed;
            case skipped:   return stop.getOrder().getStatus() == OrderStatus.cancelled ? TrackingStage.failed : TrackingStage.not_scheduled;                     // 2. done: not delivered
            case arrived:   return TrackingStage.arrived;                    // 3. driver is at the door
            default:        break;                                           // pending: look at the route
        }
        if (route.getStartedAt() == null)  return TrackingStage.scheduled;   // 4. van not out yet
        if (route.getFinishedAt() != null) return TrackingStage.failed;      // 5. route over, this stop never handled
        return stopsBefore == 0 ? TrackingStage.next                         // 6. coming to you now
                                : TrackingStage.on_the_way;                  // 7. others first
    }

    // Customer exists AND the user owns its company; a non-member gets 404, so ids in other companies stay hidden
    private Customer findCustomerForOwner(Long customerId, Long userId){
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        CompanyMember membership = companyAccess.findActiveMember(customer.getCompany().getId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        companyAccess.requireOwner(membership);
        return customer;
    }

    private Address findCompanyAddress(Long companyId, Long addressId){
        return addressRepository.findByIdAndCompanyId(addressId, companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
    }

    // Optional text: trimmed, and "" or "   " is stored as null
    private static String clean(String value){
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
