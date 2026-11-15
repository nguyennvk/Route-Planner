package com.planner.route_planner_api.feature.feedback;

// What someone would pay per month for Route Scheduler, as they chose it on the form
public enum PriceBand {
    nothing("Nothing, it should be free"),
    under_10("Under $10 a month"),
    from_10_to_20("$10 to $20 a month"),
    from_20_to_50("$20 to $50 a month"),
    from_50_to_100("$50 to $100 a month"),
    not_sure("Not sure yet");

    private final String label;

    PriceBand(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
