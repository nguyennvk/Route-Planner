package com.planner.route_planner_api.common.error;

import lombok.Getter;

@Getter 
public class TooManyRequestsException extends RuntimeException{
  private final long retryAfterSeconds;

  public TooManyRequestsException(long retryAfterSeconds) {
    this.retryAfterSeconds = retryAfterSeconds;
  }
}
