package com.planner.route_planner_api.rate_limit;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component 
public class RedisRateLimiter implements RateLimiter{
  private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
  private final StringRedisTemplate redis;
  private static final RedisScript<List> RATELIMIT_SCRIPT = RedisScript.of(
    """
      local count = redis.call('INCR', KEYS[1])
      if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
      return {count, redis.call('TTL', KEYS[1])}
    """, List.class
  );

  public RedisRateLimiter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  // 0 means allow, otherwise ttl
  public long rateAllow(String key, long window, int limit) {
    try {
      List<Long> result = redis.execute(RATELIMIT_SCRIPT, List.of(key), String.valueOf(window));
      long count = result.get(0), ttl = result.get(1);
      return count <= limit ? 0 : Math.max(ttl, 1);    
    } catch (DataAccessException e) {
      log.warn("Rate limiter unavailable, allowing request", e);
      return 0;
    }
  }
}
