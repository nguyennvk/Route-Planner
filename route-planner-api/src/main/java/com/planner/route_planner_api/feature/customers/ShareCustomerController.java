package com.planner.route_planner_api.feature.customers;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.planner.route_planner_api.feature.customers.dto.TrackingResponse;

// Mapped at /api/v1 because the routes mix /companies/{id}/customers (create, list)
// and /customers/{id} (one customer, whose company is known from the customer itself)
@RestController
@RequestMapping("/api/v1")
public class ShareCustomerController {

    private final CustomerService customerService;

    public ShareCustomerController(CustomerService customerService){
        this.customerService = customerService;
    }

    @GetMapping("/track/{trackingToken}")
    @ResponseStatus(HttpStatus.OK)
    public TrackingResponse trackOrder(
        @PathVariable String trackingToken
    ) {
        return customerService.trackOrder(trackingToken);
    }

}
