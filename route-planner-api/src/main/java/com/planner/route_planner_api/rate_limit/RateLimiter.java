package com.planner.route_planner_api.rate_limit;

public interface RateLimiter {
  boolean unAuthRateAllow(String ip);
  boolean unAuthRateAllow(Long userId);
  
}