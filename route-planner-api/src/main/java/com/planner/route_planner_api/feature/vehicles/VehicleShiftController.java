package com.planner.route_planner_api.feature.vehicles;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
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
import com.planner.route_planner_api.feature.vehicles.vehicle_shift.dto.UpdateVehicleShiftRequest;
import com.planner.route_planner_api.feature.vehicles.vehicle_shift.dto.VehicleShiftRequest;
import com.planner.route_planner_api.feature.vehicles.vehicle_shift.dto.VehicleShiftResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1")
public class VehicleShiftController {

    private final VehicleShiftService vehicleShiftService;

    public VehicleShiftController(VehicleShiftService vehicleShiftService){
        this.vehicleShiftService = vehicleShiftService;
    }

    @PostMapping("/companies/{companyId}/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleShiftResponse createShift(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long companyId,
        @Valid @RequestBody VehicleShiftRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return vehicleShiftService.createShift(userId, companyId, request);
    }

    // ?date=2026-10-26 is required; a missing or malformed date is an automatic 400
    @GetMapping("/companies/{companyId}/shifts")
    @ResponseStatus(HttpStatus.OK)
    public PageResponse<VehicleShiftResponse> listShifts(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long companyId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) Long driverUserId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return vehicleShiftService.listShifts(userId, companyId, date, driverUserId, page, size);
    }

    @PatchMapping("/shifts/{shiftId}")
    @ResponseStatus(HttpStatus.OK)
    public VehicleShiftResponse updateShift(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shiftId,
        @Valid @RequestBody UpdateVehicleShiftRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return vehicleShiftService.updateShift(userId, shiftId, request);
    }

    @DeleteMapping("/shifts/{shiftId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteShift(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shiftId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        vehicleShiftService.deleteShift(userId, shiftId);
    }
}
