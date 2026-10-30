package com.planner.route_planner_api.feature.orders.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.planner.route_planner_api.feature.addresses.dto.CreateAddressResponse;
import com.planner.route_planner_api.feature.customers.dto.CustomerResponse;
import com.planner.route_planner_api.feature.orders.Order;
import com.planner.route_planner_api.feature.orders.OrderStatus;

public record OrderResponse(
    Long id,
    CustomerResponse customer,
    CreateAddressResponse address,
    List<OrderItemResponse> items,
    BigDecimal totalWeightKg,
    OrderStatus status,
    Instant windowStart,
    Instant windowEnd,
    boolean windowStrict,
    int serviceMinutes,
    String notes,
    Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
            order.getId(),
            CustomerResponse.from(order.getCustomer()),
            CreateAddressResponse.from(order.getAddress()),
            order.getItems().stream().map(OrderItemResponse::from).toList(),
            order.getTotalWeightKg(),
            order.getStatus(),
            order.getWindowStart(),
            order.getWindowEnd(),
            order.isWindowStrict(),
            order.getServiceMinutes(),
            order.getNotes(),
            order.getCreatedAt()
        );
    }
}
