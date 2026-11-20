package com.planner.route_planner_api.rate_limit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component 
public class RedisRateLimiter implements RateLimiter{
  private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
  private final StringRedisTemplate redis;

  RedisRateLimiter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public boolean unAuthRateAllow(String ip) {
    return true;
  }

  public boolean unAuthRateAllow(Long userId) {
    return true;
  }
  

}
