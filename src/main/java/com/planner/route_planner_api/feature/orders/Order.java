package com.planner.route_planner_api.feature.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.planner.route_planner_api.common.error.BadRequestException;
import com.planner.route_planner_api.common.persistence.BaseEntity;
import com.planner.route_planner_api.feature.addresses.Address;
import com.planner.route_planner_api.feature.companies.Company;
import com.planner.route_planner_api.feature.customers.Customer;
import com.planner.route_planner_api.feature.customers.Item;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;

// One delivery: what (items), to whom (customer), where (address) and when (time window).
@Entity
@Table(name = "orders")
@Getter
public class Order extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    // Can differ from the customer's default address (e.g. delivering to their office today)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)                    // a real Postgres ENUM, not varchar
    @Column(nullable = false, columnDefinition = "order_status")
    private OrderStatus status = OrderStatus.pending;

    // Both optional: no window means "any time during the driver's shift"
    @Column(name = "window_start")
    private Instant windowStart;

    @Column(name = "window_end")
    private Instant windowEnd;

    // true = the solver must hit the window (hard constraint); false = being late only costs points
    @Column(name = "window_strict", nullable = false)
    private boolean windowStrict;

    // Minutes spent at the door (parking, handing over, signature)
    @Column(name = "service_minutes", nullable = false)
    private int serviceMinutes;

    // Sum of the lines, stored so the solver doesn't add them up again. Kept in sync by replaceItems().
    @Column(name = "total_weight_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalWeightKg = BigDecimal.ZERO;

    @Column
    private String notes;

    // cascade ALL: lines are saved/updated with the order. orphanRemoval: a line taken out of this list is deleted.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public Order(Company company, Customer customer, Address address,
                 Instant windowStart, Instant windowEnd, boolean windowStrict, int serviceMinutes, String notes) {
        requireValidWindow(windowStart, windowEnd);
        this.company = company;
        this.customer = customer;
        this.address = address;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.windowStrict = windowStrict;
        this.serviceMinutes = serviceMinutes;
        this.notes = notes;
    }

    // Same rule as the table's CHECK (window_end > window_start), checked here so the client gets a 400, not a 500
    public static void requireValidWindow(Instant windowStart, Instant windowEnd) {
        if (windowStart != null && windowEnd != null && !windowEnd.isAfter(windowStart)) {
            throw new BadRequestException("windowEnd must be after windowStart");
        }
    }

    public boolean isEditable() {
        return status == OrderStatus.pending;
    }

    // Makes the order's lines match `wanted` (item -> quantity) and recalculates the total weight.
    // Updates lines in place instead of "delete all, insert all": Hibernate runs INSERTs before DELETEs
    // when it flushes, so re-adding an item that was just removed would hit the (order_id, item_id) key twice.
    public void replaceItems(Map<Item, Integer> wanted) {
        Iterator<OrderItem> existing = items.iterator();
        while (existing.hasNext()) {
            OrderItem line = existing.next();
            Integer quantity = findQuantity(wanted, line.getItem());
            if (quantity == null) {
                existing.remove();                          // orphanRemoval deletes the row
            } else {
                line.changeQuantity(quantity);
            }
        }
        for (Map.Entry<Item, Integer> entry : wanted.entrySet()) {
            boolean alreadyThere = items.stream().anyMatch(line -> sameItem(line.getItem(), entry.getKey()));
            if (!alreadyThere) {
                items.add(new OrderItem(this, entry.getKey(), entry.getValue()));
            }
        }
        recalculateTotalWeight();
    }

    // Also called when an item's unit weight changes (see ItemService.updateItem)
    public void recalculateTotalWeight() {
        BigDecimal total = items.stream()
            .map(OrderItem::getWeightKg)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.totalWeightKg = total.setScale(2, RoundingMode.HALF_UP);        // numeric(10, 2)
    }

    public void changeAddress(Address address) {
        this.address = address;
    }

    public void changeWindow(Instant windowStart, Instant windowEnd, boolean windowStrict) {
        requireValidWindow(windowStart, windowEnd);
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.windowStrict = windowStrict;
    }

    public void changeServiceMinutes(int serviceMinutes) {
        this.serviceMinutes = serviceMinutes;
    }

    public void changeNotes(String notes) {
        this.notes = notes;
    }

    public void cancel() {
        this.status = OrderStatus.cancelled;
    }

    // Compare by id: the lines may hold Hibernate proxies, which are never == to the loaded Item object
    private static boolean sameItem(Item a, Item b) {
        return a.getId().equals(b.getId());
    }

    private static Integer findQuantity(Map<Item, Integer> wanted, Item item) {
        for (Map.Entry<Item, Integer> entry : wanted.entrySet()) {
            if (sameItem(entry.getKey(), item)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
