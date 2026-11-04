package com.planner.route_planner_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RoutePlannerApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(RoutePlannerApiApplication.class, args);
	}

}
