package com.planner.route_planner_api.feature.orders;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// JpaSpecificationExecutor adds findAll(Specification, Pageable): a query built from
// only the filters the client sent (see OrderService.listOrders)
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    // Orders in one status that contain an item ("items.item.id" walks Order -> OrderItem -> Item).
    // Distinct: an order has at most one line per item, but the join could still repeat it.
    List<Order> findDistinctByItemsItemIdAndStatus(Long itemId, OrderStatus status);
}
