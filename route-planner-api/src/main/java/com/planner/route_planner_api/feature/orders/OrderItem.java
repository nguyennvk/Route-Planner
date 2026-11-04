package com.planner.route_planner_api.feature.orders;

import java.math.BigDecimal;

import com.planner.route_planner_api.feature.customers.Item;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;

// One line of an order: an item and how many. Saved and deleted through Order (cascade), never on its own.
// Not a BaseEntity: this table has a two-column key and no id/created_at columns.
@Entity
@Table(name = "order_items")
@Getter
public class OrderItem {

    @EmbeddedId
    private OrderItemId id;

    @MapsId("orderId")                                    // fills id.orderId from this relationship, once the order has an id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @MapsId("itemId")                                     // fills id.itemId from this relationship
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(nullable = false)
    private int quantity;

    protected OrderItem() {}

    OrderItem(Order order, Item item, int quantity) {
        // order.getId() is still null for a new order; Hibernate fills it in through @MapsId on insert
        this.id = new OrderItemId(order.getId(), item.getId());
        this.order = order;
        this.item = item;
        this.quantity = quantity;
    }

    void changeQuantity(int quantity) {
        this.quantity = quantity;
    }

    // Weight of the whole line: one unit's weight times the quantity
    public BigDecimal getWeightKg() {
        return item.getWeightKg().multiply(BigDecimal.valueOf(quantity));
    }
}
