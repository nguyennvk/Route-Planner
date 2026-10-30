package com.planner.route_planner_api.feature.orders;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planner.route_planner_api.common.error.BadRequestException;
import com.planner.route_planner_api.common.error.ConflictException;
import com.planner.route_planner_api.common.error.ResourceNotFoundException;
import com.planner.route_planner_api.common.web.PageResponse;
import com.planner.route_planner_api.feature.addresses.Address;
import com.planner.route_planner_api.feature.addresses.AddressRepository;
import com.planner.route_planner_api.feature.companies.Company;
import com.planner.route_planner_api.feature.companies.CompanyAccess;
import com.planner.route_planner_api.feature.companies.CompanyMember;
import com.planner.route_planner_api.feature.customers.Customer;
import com.planner.route_planner_api.feature.customers.CustomerRepository;
import com.planner.route_planner_api.feature.customers.Item;
import com.planner.route_planner_api.feature.customers.ItemRepository;
import com.planner.route_planner_api.feature.orders.dto.OrderItemRequest;
import com.planner.route_planner_api.feature.orders.dto.OrderRequest;
import com.planner.route_planner_api.feature.orders.dto.OrderResponse;
import com.planner.route_planner_api.feature.orders.dto.UpdateOrderRequest;

import jakarta.persistence.criteria.Predicate;

// Every order endpoint is owner-only, reads included: orders carry customers' phone numbers and emails.
// Drivers will see their own stops through the route share link instead.
@Service
public class OrderService {

    private static final int DEFAULT_SERVICE_MINUTES = 5;       // same as the column default

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ItemRepository itemRepository;
    private final AddressRepository addressRepository;
    private final CompanyAccess companyAccess;

    public OrderService(
            OrderRepository orderRepository,
            CustomerRepository customerRepository,
            ItemRepository itemRepository,
            AddressRepository addressRepository,
            CompanyAccess companyAccess
        ){
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.itemRepository = itemRepository;
        this.addressRepository = addressRepository;
        this.companyAccess = companyAccess;
    }

    @Transactional
    public OrderResponse createOrder(Long userId, Long companyId, OrderRequest request){
        CompanyMember membership = companyAccess.requireMember(companyId, userId);
        companyAccess.requireOwner(membership);
        Company company = membership.getCompany();

        Customer customer = customerRepository.findByIdAndCompanyId(request.customerId(), companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));
        Address address = request.addressId() != null
            ? findCompanyAddress(companyId, request.addressId())
            : defaultAddressOf(customer);
        Map<Item, Integer> lines = findCompanyItems(companyId, request.items());

        // The constructor rejects windowEnd <= windowStart with a 400
        Order order = new Order(company, customer, address,
            request.windowStart(), request.windowEnd(),
            Boolean.TRUE.equals(request.windowStrict()),
            request.serviceMinutes() == null ? DEFAULT_SERVICE_MINUTES : request.serviceMinutes(),
            cleanNotes(request.notes()));
        order.replaceItems(lines);
        return OrderResponse.from(orderRepository.save(order));
    }

    // date: orders whose window STARTS on that day, midnight to midnight in the company's timezone.
    // Orders without a windowStart have no day, so they only show up when date isn't sent.
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listOrders(
            Long userId, Long companyId, LocalDate date, OrderStatus status, Long customerId, int page, int size){
        CompanyMember membership = companyAccess.requireMember(companyId, userId);
        companyAccess.requireOwner(membership);

        Instant from;
        Instant to;
        if (date != null) {
            ZoneId zone = ZoneId.of(membership.getCompany().getTimezone());
            from = date.atStartOfDay(zone).toInstant();
            to = date.plusDays(1).atStartOfDay(zone).toInstant();
        } else {
            from = null;
            to = null;
        }

        // Build the WHERE clause from only the filters the client sent
        Specification<Order> filters = (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.equal(root.get("company").get("id"), companyId));
            if (from != null) {
                conditions.add(cb.greaterThanOrEqualTo(root.get("windowStart"), from));
                conditions.add(cb.lessThan(root.get("windowStart"), to));
            }
            if (status != null) {
                conditions.add(cb.equal(root.get("status"), status));
            }
            if (customerId != null) {
                conditions.add(cb.equal(root.get("customer").get("id"), customerId));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };

        // Earliest window first; id breaks ties so pages never overlap or skip rows
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("windowStart", "id"));
        return PageResponse.from(orderRepository.findAll(filters, pageRequest).map(OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long userId, Long orderId){
        return OrderResponse.from(findOrderForOwner(orderId, userId));
    }

    @Transactional
    public OrderResponse updateOrder(Long userId, Long orderId, UpdateOrderRequest request){
        Order order = findOrderForOwner(orderId, userId);
        requireEditable(order);
        Long companyId = order.getCompany().getId();

        // PATCH: work out the new values first (null = keep the current one), check them, then apply
        Instant windowStart = request.windowStart() != null ? request.windowStart() : order.getWindowStart();
        Instant windowEnd = request.windowEnd() != null ? request.windowEnd() : order.getWindowEnd();
        boolean windowStrict = request.windowStrict() != null ? request.windowStrict() : order.isWindowStrict();
        order.changeWindow(windowStart, windowEnd, windowStrict);       // rejects windowEnd <= windowStart with a 400

        if (request.addressId() != null) {
            order.changeAddress(findCompanyAddress(companyId, request.addressId()));
        }
        if (request.items() != null) {
            order.replaceItems(findCompanyItems(companyId, request.items()));
        }
        if (request.serviceMinutes() != null) {
            order.changeServiceMinutes(request.serviceMinutes());
        }
        if (request.notes() != null) {
            order.changeNotes(cleanNotes(request.notes()));
        }
        // No save() needed: Hibernate sees the changes and runs the UPDATEs when the transaction commits
        return OrderResponse.from(order);
    }

    // Allowed before the driver leaves: pending, or planned (in a published plan but not on the road yet)
    @Transactional
    public OrderResponse cancelOrder(Long userId, Long orderId){
        Order order = findOrderForOwner(orderId, userId);
        if (order.getStatus() != OrderStatus.pending && order.getStatus() != OrderStatus.planned) {
            throw new ConflictException("An order that is " + order.getStatus() + " can't be cancelled");
        }
        order.cancel();
        return OrderResponse.from(order);
    }

    // Order exists AND the user owns its company; a non-member gets 404, so ids in other companies stay hidden
    private Order findOrderForOwner(Long orderId, Long userId){
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        CompanyMember membership = companyAccess.findActiveMember(order.getCompany().getId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        companyAccess.requireOwner(membership);
        return order;
    }

    // Once an order is in a plan, changing it would make the plan wrong. Cancel it and create a new one instead.
    private void requireEditable(Order order){
        if (!order.isEditable()) {
            throw new ConflictException("Only pending orders can be changed; this one is " + order.getStatus());
        }
    }

    private Address findCompanyAddress(Long companyId, Long addressId){
        return addressRepository.findByIdAndCompanyId(addressId, companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
    }

    private static Address defaultAddressOf(Customer customer){
        if (customer.getDefaultAddress() == null) {
            throw new BadRequestException("This customer has no default address; send addressId");
        }
        return customer.getDefaultAddress();
    }

    // Loads the items in one query and returns item -> quantity, in the order the client sent them.
    // Rejects the same item twice (the table allows one line per item) and items from other companies.
    private Map<Item, Integer> findCompanyItems(Long companyId, List<OrderItemRequest> lines){
        Set<Long> seen = new HashSet<>();
        for (OrderItemRequest line : lines) {
            if (!seen.add(line.itemId())) {
                throw new BadRequestException("Item " + line.itemId() + " is listed twice; put the total in one line");
            }
        }

        Map<Long, Item> itemsById = itemRepository.findByCompanyIdAndIdIn(companyId, seen).stream()
            .collect(Collectors.toMap(Item::getId, Function.identity()));

        Map<Item, Integer> result = new LinkedHashMap<>();
        for (OrderItemRequest line : lines) {
            Item item = itemsById.get(line.itemId());
            if (item == null) {
                throw new ResourceNotFoundException("Item", line.itemId());
            }
            result.put(item, line.quantity());
        }
        return result;
    }

    // "" or "   " is stored as no notes
    private static String cleanNotes(String notes){
        if (notes == null || notes.isBlank()) {
            return null;
        }
        return notes.strip();
    }
}
