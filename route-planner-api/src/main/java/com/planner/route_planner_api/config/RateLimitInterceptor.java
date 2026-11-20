package com.planner.route_planner_api.config;

import java.time.Duration;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.planner.route_planner_api.common.error.TooManyRequestsException;
import com.planner.route_planner_api.rate_limit.RateLimiter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component 
public class RateLimitInterceptor implements HandlerInterceptor{
  private final RateLimiter limiter;
  
  public RateLimitInterceptor(RateLimiter limiter) {
    this.limiter = limiter;
  }

  @Override 
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    String path = request.getRequestURI();
    String key;
    int limitPerMinnute;
    if (path.startsWith("/api/v1/track/")) {
      key = "rl:track:" + request.getRemoteAddr();
      limitPerMinnute = 10;
    } else {
      key = "rl:geo:" + currentUserId();
      limitPerMinnute = 20;
    }
    long wait = limiter.rateAllow(key, 60, limitPerMinnute);
    if (wait > 0) throw new TooManyRequestsException(wait);
    return true;
  }

  public String currentUserId() {
    return SecurityContextHolder.getContext().getAuthentication().getName();
  }

}
