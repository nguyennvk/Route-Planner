package com.planner.route_planner_api.feature.vehicles;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planner.route_planner_api.common.error.ResourceNotFoundException;
import com.planner.route_planner_api.common.web.PageResponse;
import com.planner.route_planner_api.feature.companies.CompanyAccess;
import com.planner.route_planner_api.feature.companies.CompanyMember;
import com.planner.route_planner_api.feature.depots.Depot;
import com.planner.route_planner_api.feature.depots.DepotRepository;
import com.planner.route_planner_api.feature.vehicles.vehicle.dto.UpdateVehicleRequest;
import com.planner.route_planner_api.feature.vehicles.vehicle.dto.VehicleRequest;
import com.planner.route_planner_api.feature.vehicles.vehicle.dto.VehicleResponse;

import jakarta.persistence.criteria.Predicate;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final DepotRepository depotRepository;
    private final CompanyAccess companyAccess;

    public VehicleService(
            VehicleRepository vehicleRepository,
            DepotRepository depotRepository,
            CompanyAccess companyAccess
        ){
        this.vehicleRepository = vehicleRepository;
        this.depotRepository = depotRepository;
        this.companyAccess = companyAccess;
    }

    @Transactional
    public VehicleResponse createVehicle(Long userId, Long companyId, VehicleRequest request){
        CompanyMember membership = companyAccess.requireMember(companyId, userId);
        companyAccess.requireOwner(membership);

        Depot depot = findCompanyDepot(companyId, request.homeDepotId());
        Vehicle vehicle = vehicleRepository.save(new Vehicle(
            membership.getCompany(), request.name().trim(), request.type(),
            request.capacityKg(), request.capacityM3(), depot));
        return VehicleResponse.from(vehicle);
    }

    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> listVehicles(Long userId, Long companyId, Boolean active, VehicleType type, int page, int size){
        companyAccess.requireMember(companyId, userId);

        // Build the WHERE clause from only the filters the client sent
        Specification<Vehicle> filters = (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.equal(root.get("company").get("id"), companyId));
            if (active != null) {
                conditions.add(cb.equal(root.get("active"), active));
            }
            if (type != null) {
                conditions.add(cb.equal(root.get("type"), type));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("id"));
        return PageResponse.from(vehicleRepository.findAll(filters, pageRequest).map(VehicleResponse::from));
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicle(Long userId, Long vehicleId){
        Vehicle vehicle = findVehicleForMember(vehicleId, userId);
        return VehicleResponse.from(vehicle);
    }

    @Transactional
    public VehicleResponse updateVehicle(Long userId, Long vehicleId, UpdateVehicleRequest request){
        Vehicle vehicle = findVehicleForOwner(vehicleId, userId);

        // PATCH: only touch the fields the client sent
        // No save() needed: Hibernate sees the change and runs the UPDATE when the transaction commits
        if (request.name() != null) {
            vehicle.rename(request.name().trim());
        }
        if (request.type() != null) {
            vehicle.changeType(request.type());
        }
        if (request.capacityKg() != null || request.capacityM3() != null) {
            vehicle.changeCapacity(
                request.capacityKg() != null ? request.capacityKg() : vehicle.getCapacityKg(),
                request.capacityM3() != null ? request.capacityM3() : vehicle.getCapacityM3());
        }
        if (request.homeDepotId() != null) {
            vehicle.moveToDepot(findCompanyDepot(vehicle.getCompany().getId(), request.homeDepotId()));
        }
        if (request.active() != null) {
            if (request.active()) {
                vehicle.activate();
            } else {
                vehicle.deactivate();
            }
        }
        return VehicleResponse.from(vehicle);
    }

    // Soft delete: past routes and shifts keep pointing to the vehicle
    @Transactional
    public void deactivateVehicle(Long userId, Long vehicleId){
        Vehicle vehicle = findVehicleForOwner(vehicleId, userId);
        vehicle.deactivate();
    }

    // Vehicle exists AND the user is an active member of its company; otherwise 404 either way,
    // so users can't probe which vehicle ids exist in other companies
    private Vehicle findVehicleForMember(Long vehicleId, Long userId){
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId));
        companyAccess.findActiveMember(vehicle.getCompany().getId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId));
        return vehicle;
    }

    private Vehicle findVehicleForOwner(Long vehicleId, Long userId){
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId));
        CompanyMember membership = companyAccess.findActiveMember(vehicle.getCompany().getId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId));
        companyAccess.requireOwner(membership);
        return vehicle;
    }

    // A depot from another company counts as "not found"
    private Depot findCompanyDepot(Long companyId, Long depotId){
        return depotRepository.findByIdAndCompanyId(depotId, companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Depot", depotId));
    }
}
