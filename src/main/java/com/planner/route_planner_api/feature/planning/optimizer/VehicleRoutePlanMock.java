package com.planner.route_planner_api.feature.planning.optimizer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// A tiny hand-made problem for trying the solver without the database or OSRM:
// 1 van at the depot and 2 visits, at real addresses from the seed data (Maple Water Co.)
public final class VehicleRoutePlanMock {

    private VehicleRoutePlanMock() {}

    public static VehicleRoutePlan create() {
        Location depot = new Location(43.643878, -79.377975);       // 40 Bay St
        Location cityHall = new Location(43.651504, -79.382497);    // Toronto City Hall, 100 Queen St W
        Location ago = new Location(43.654328, -79.391454);         // Art Gallery of Ontario, 317 Dundas St W

        // Driving times in seconds, made up for the mock; OSRM provides real ones later.
        // Every location needs a time to every other location it can be driven to
        depot.setDrivingTimeInSeconds(Map.of(cityHall, 420L, ago, 600L));
        cityHall.setDrivingTimeInSeconds(Map.of(depot, 450L, ago, 300L));
        ago.setDrivingTimeInSeconds(Map.of(depot, 640L, cityHall, 320L));

        LocalDateTime day = LocalDateTime.of(2026, 11, 2, 0, 0);

        PlannedVehicle van = new PlannedVehicle("van-1", 500, depot, day.withHour(8), day.withHour(18));   // 500 kg, leaves 08:00

        Visit cityHallVisit = new Visit(1L, "Toronto City Hall", cityHall, 62_000,     // 62 kg
            day.withHour(9), day.withHour(12), true, Duration.ofMinutes(5));           // strict 09:00–12:00
        Visit agoVisit = new Visit(2L, "Art Gallery of Ontario", ago, 41_000,          // 41 kg
            day.withHour(13), day.withHour(16), false, Duration.ofMinutes(10));        // loose 13:00–16:00

        // Mutable lists: Timefold copies and changes them while solving
        return new VehicleRoutePlan(
            "Mock plan: 1 van, 2 visits",
            day.withHour(8),
            day.withHour(18),
            new ArrayList<>(List.of(van)),
            new ArrayList<>(List.of(cityHallVisit, agoVisit)));
    }
}
