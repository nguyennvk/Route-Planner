package com.planner.route_planner_api.common.error;

// Logged in, but not allowed to do this (403). Not logged in at all is 401.
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
