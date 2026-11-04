package com.planner.route_planner_api.common.error;

// An outside service we depend on (e.g. the geocoder) can't answer right now -> 503
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String msg){
        super(msg);
    }
}
