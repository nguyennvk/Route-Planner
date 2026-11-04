package com.planner.route_planner_api.common.error;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String msg){
        super(msg);
    }
}
