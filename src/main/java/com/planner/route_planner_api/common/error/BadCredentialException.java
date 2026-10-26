package com.planner.route_planner_api.common.error;

public class BadCredentialException extends RuntimeException{
    public BadCredentialException(String msg){
        super(msg);
    }
}
