package com.planner.route_planner_api.feature.orders;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

// The two-column primary key (order_id, item_id) of order_items
@Embeddable
@Getter
public class OrderItemId implements Serializable {

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "item_id")
    private Long itemId;

    protected OrderItemId() {}

    public OrderItemId(Long orderId, Long itemId) {
        this.orderId = orderId;
        this.itemId = itemId;
    }

    // Two keys are equal when both columns match; Hibernate relies on this
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderItemId other)) return false;
        return Objects.equals(orderId, other.orderId) && Objects.equals(itemId, other.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, itemId);
    }
}
