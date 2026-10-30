package com.planner.route_planner_api.feature.orders;

// Spelled exactly like the Postgres enum labels in V1
public enum OrderStatus {
    pending,            // created, not in a plan yet: the only status that can still be edited
    planned,            // in a published plan
    out_for_delivery,   // the driver started the route
    delivered,
    failed,
    cancelled
}
