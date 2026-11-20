package com.planner.route_planner_api.rate_limit;

public interface RateLimiter {
  long rateAllow(String key, long window, int limit); 
}