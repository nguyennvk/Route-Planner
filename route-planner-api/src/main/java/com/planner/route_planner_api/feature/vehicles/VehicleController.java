package com.planner.route_planner_api.feature.vehicles;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.planner.route_planner_api.common.web.PageResponse;
import com.planner.route_planner_api.feature.vehicles.vehicle.dto.UpdateVehicleRequest;
import com.planner.route_planner_api.feature.vehicles.vehicle.dto.VehicleRequest;
import com.planner.route_planner_api.feature.vehicles.vehicle.dto.VehicleResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// Mapped at /api/v1 because the routes mix /companies/{id}/vehicles (create, list)
// and /vehicles/{id} (one vehicle, whose company is known from the vehicle itself)
@RestController
@RequestMapping("/api/v1")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService){
        this.vehicleService = vehicleService;
    }

    @PostMapping("/companies/{companyId}/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleResponse createVehicle(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long companyId,
        @Valid @RequestBody VehicleRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return vehicleService.createVehicle(userId, companyId, request);
    }

    @GetMapping("/companies/{companyId}/vehicles")
    @ResponseStatus(HttpStatus.OK)
    public PageResponse<VehicleResponse> listVehicles(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long companyId,
        @RequestParam(required = false) Boolean active,
        @RequestParam(required = false) VehicleType type,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return vehicleService.listVehicles(userId, companyId, active, type, page, size);
    }

    @GetMapping("/vehicles/{vehicleId}")
    @ResponseStatus(HttpStatus.OK)
    public VehicleResponse getVehicle(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long vehicleId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return vehicleService.getVehicle(userId, vehicleId);
    }

    @PatchMapping("/vehicles/{vehicleId}")
    @ResponseStatus(HttpStatus.OK)
    public VehicleResponse updateVehicle(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long vehicleId,
        @Valid @RequestBody UpdateVehicleRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return vehicleService.updateVehicle(userId, vehicleId, request);
    }

    // Soft delete (active = false)
    @DeleteMapping("/vehicles/{vehicleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateVehicle(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long vehicleId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        vehicleService.deactivateVehicle(userId, vehicleId);
    }
}
