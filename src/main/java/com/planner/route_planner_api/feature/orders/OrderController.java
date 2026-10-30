package com.planner.route_planner_api.feature.orders;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.planner.route_planner_api.common.web.PageResponse;
import com.planner.route_planner_api.feature.orders.dto.OrderRequest;
import com.planner.route_planner_api.feature.orders.dto.OrderResponse;
import com.planner.route_planner_api.feature.orders.dto.UpdateOrderRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// Mapped at /api/v1 because the routes mix /companies/{id}/orders (create, list)
// and /orders/{id} (one order, whose company is known from the order itself)
@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService){
        this.orderService = orderService;
    }

    @PostMapping("/companies/{companyId}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long companyId,
        @Valid @RequestBody OrderRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return orderService.createOrder(userId, companyId, request);
    }

    @GetMapping("/companies/{companyId}/orders")
    @ResponseStatus(HttpStatus.OK)
    public PageResponse<OrderResponse> listOrders(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long companyId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) OrderStatus status,
        @RequestParam(required = false) Long customerId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return orderService.listOrders(userId, companyId, date, status, customerId, page, size);
    }

    @GetMapping("/orders/{orderId}")
    @ResponseStatus(HttpStatus.OK)
    public OrderResponse getOrder(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long orderId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return orderService.getOrder(userId, orderId);
    }

    @PatchMapping("/orders/{orderId}")
    @ResponseStatus(HttpStatus.OK)
    public OrderResponse updateOrder(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long orderId,
        @Valid @RequestBody UpdateOrderRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return orderService.updateOrder(userId, orderId, request);
    }

    // A POST action, not DELETE: the order stays in the database with status "cancelled"
    @PostMapping("/orders/{orderId}/cancel")
    @ResponseStatus(HttpStatus.OK)
    public OrderResponse cancelOrder(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long orderId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return orderService.cancelOrder(userId, orderId);
    }
}
