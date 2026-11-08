package com.planner.route_planner_api.feature.planning.optimizer;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter 
@Setter 
public class Location{
    private Double lattitude;
    private Double longitude;
    private Map<Location, Long> drivingTimeInSeconds;
    private Map<Location, Long> drivingDistanceMeter;

    public Location(double lattitude, double longitude){
        this.lattitude = lattitude;
        this.longitude = longitude;
    }

    public long getDrivingTimeTo(Location location) {
        return drivingTimeInSeconds.get(location);
    }

    public long getDrivingDistanceTo(Location location){
        return drivingDistanceMeter.get(location);
    }
    
}
