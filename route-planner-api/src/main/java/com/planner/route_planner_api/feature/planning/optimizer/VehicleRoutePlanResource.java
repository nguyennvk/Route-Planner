package com.planner.route_planner_api.feature.planning.optimizer;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverManager;
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
