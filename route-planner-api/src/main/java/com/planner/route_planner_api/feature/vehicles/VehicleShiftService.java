package com.planner.route_planner_api.feature.vehicles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planner.route_planner_api.common.error.BadRequestException;
import com.planner.route_planner_api.common.error.ConflictException;
import com.planner.route_planner_api.common.error.ResourceNotFoundException;
import com.planner.route_planner_api.common.web.PageResponse;
import com.planner.route_planner_api.feature.auth.User;
import com.planner.route_planner_api.feature.companies.CompanyAccess;
import com.planner.route_planner_api.feature.companies.CompanyMember;
import com.planner.route_planner_api.feature.vehicles.vehicle_shift.dto.UpdateVehicleShiftRequest;
import com.planner.route_planner_api.feature.vehicles.vehicle_shift.dto.VehicleShiftRequest;
import com.planner.route_planner_api.feature.vehicles.vehicle_shift.dto.VehicleShiftResponse;

@Service
public class VehicleShiftService {

    // Passed as "ignore this shift" when creating, since there's no shift to ignore yet (ids start at 1)
    private static final Long NO_SHIFT = -1L;

    private final VehicleShiftRepository vehicleShiftRepository;
    private final VehicleRepository vehicleRepository;
    private final CompanyAccess companyAccess;

    public VehicleShiftService(
            VehicleShiftRepository vehicleShiftRepository,
            VehicleRepository vehicleRepository,
            CompanyAccess companyAccess
        ){
        this.vehicleShiftRepository = vehicleShiftRepository;
        this.vehicleRepository = vehicleRepository;
        this.companyAccess = companyAccess;
    }

    @Transactional
    public VehicleShiftResponse createShift(Long userId, Long companyId, VehicleShiftRequest request){
        CompanyMember membership = companyAccess.requireMember(companyId, userId);
        companyAccess.requireOwner(membership);

        // A vehicle from another company counts as "not found"
        Vehicle vehicle = vehicleRepository.findByIdAndCompanyId(request.vehicleId(), companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle", request.vehicleId()));
        if (!vehicle.isActive()) {
            throw new BadRequestException("Vehicle is inactive");
        }
        User driver = request.driverUserId() == null ? null : findCompanyDriver(companyId, request.driverUserId());

        // The constructor rejects endAt <= startAt with a 400
        VehicleShift shift = new VehicleShift(vehicle, driver, request.startAt(), request.endAt());
        checkNoOverlap(vehicle, driver, request.startAt(), request.endAt(), NO_SHIFT);

        return VehicleShiftResponse.from(vehicleShiftRepository.save(shift));
    }

    // Shifts that overlap the given day, where "day" is midnight to midnight in the company's timezone
    @Transactional(readOnly = true)
    public PageResponse<VehicleShiftResponse> listShifts(Long userId, Long companyId, LocalDate date, Long driverUserId, int page, int size){
        CompanyMember membership = companyAccess.requireMember(companyId, userId);

        ZoneId zone = ZoneId.of(membership.getCompany().getTimezone());
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<VehicleShift> shifts = (driverUserId == null)
            ? vehicleShiftRepository.findForCompanyBetween(companyId, from, to, pageRequest)
            : vehicleShiftRepository.findForCompanyAndDriverBetween(companyId, driverUserId, from, to, pageRequest);
        return PageResponse.from(shifts.map(VehicleShiftResponse::from));
    }

    @Transactional
    public VehicleShiftResponse updateShift(Long userId, Long shiftId, UpdateVehicleShiftRequest request){
        VehicleShift shift = findShiftForOwner(shiftId, userId);
        Long companyId = shift.getVehicle().getCompany().getId();

        // PATCH: work out the new values first (null = keep the current one), check them, then apply
        User driver = request.driverUserId() == null ? shift.getDriver() : findCompanyDriver(companyId, request.driverUserId());
        Instant startAt = request.startAt() == null ? shift.getStartAt() : request.startAt();
        Instant endAt = request.endAt() == null ? shift.getEndAt() : request.endAt();

        if (!endAt.isAfter(startAt)) {
            throw new BadRequestException("Shift must end after it starts");
        }
        checkNoOverlap(shift.getVehicle(), driver, startAt, endAt, shift.getId());

        // No save() needed: Hibernate sees the change and runs the UPDATE when the transaction commits
        shift.reschedule(startAt, endAt);
        if (driver != null) {
            shift.assignDriver(driver);
        }
        return VehicleShiftResponse.from(shift);
    }

    @Transactional
    public void deleteShift(Long userId, Long shiftId){
        VehicleShift shift = findShiftForOwner(shiftId, userId);
        vehicleShiftRepository.delete(shift);
    }

    // Shift exists AND the user owns its company; a non-member gets 404, so ids in other companies stay hidden
    private VehicleShift findShiftForOwner(Long shiftId, Long userId){
        VehicleShift shift = vehicleShiftRepository.findWithDetails(shiftId)
            .orElseThrow(() -> new ResourceNotFoundException("Shift", shiftId));
        CompanyMember membership = companyAccess.findActiveMember(shift.getVehicle().getCompany().getId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Shift", shiftId));
        companyAccess.requireOwner(membership);
        return shift;
    }

    // The driver must be an active member of the same company (any role: in small companies the owner drives too)
    private User findCompanyDriver(Long companyId, Long driverUserId){
        return companyAccess.findActiveMember(companyId, driverUserId)
            .map(CompanyMember::getUser)
            .orElseThrow(() -> new BadRequestException("Driver must be an active member of this company"));
    }

    // Same rules as the table's EXCLUDE constraints, checked first so the client gets a clear message.
    // The database constraint still has the final say if two requests race.
    private void checkNoOverlap(Vehicle vehicle, User driver, Instant startAt, Instant endAt, Long ignoreShiftId){
        if (vehicleShiftRepository.countVehicleOverlaps(vehicle.getId(), startAt, endAt, ignoreShiftId) > 0) {
            throw new ConflictException("Vehicle already has a shift at that time");
        }
        if (driver != null && vehicleShiftRepository.countDriverOverlaps(driver.getId(), startAt, endAt, ignoreShiftId) > 0) {
            throw new ConflictException("Driver already has a shift at that time");
        }
    }
}
