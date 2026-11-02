package com.planner.route_planner_api.feature.planning.optimizer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.springframework.util.RouteMatcher.Route;

import com.planner.route_planner_api.feature.vehicles.Vehicle;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverStatus;
import lombok.Getter;

@Getter
public class VehicleRoutePlanResource {
    private final SolverManager<VehicleRoutePlan> solverManager;

    private final SolutionManager<VehicleRoutePlan, HardMediumSoftScore> solutionManager;

    public VehicleRoutePlanResource(SolverManager<VehicleRoutePlan> solverManager,
                                    SolutionManager<VehicleRoutePlan, HardMediumSoftScore> solutionManager) {
        this.solverManager = solverManager;
        this.solutionManager = solutionManager;
    }

    public String solve(VehicleRoutePlan problem) throws InterruptedException, ExecutionException{
        String jobId = UUID.randomUUID().toString();
        VehicleRoutePlan vehicleRoutePlan = VehicleRoutePlanMock.create();
        solverManager.solve(jobId, vehicleRoutePlan).getFinalBestSolution();
        return jobId;
    }


}
