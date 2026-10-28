package com.planner.route_planner_api.feature.vehicles;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VehicleShiftRepository extends JpaRepository<VehicleShift, Long> {

    // A shift with its vehicle and driver loaded in the same query
    @Query("""
            select s from VehicleShift s
            join fetch s.vehicle
            left join fetch s.driver
            where s.id = :id
            """)
    Optional<VehicleShift> findWithDetails(Long id);

    // Shifts of the company that overlap [from, to).
    // "left join fetch": the driver is optional, a plain join would drop shifts without one.
    // countQuery: Spring needs a separate count for the page totals, and "fetch" isn't allowed in a count.
    @Query(value = """
            select s from VehicleShift s
            join fetch s.vehicle v
            left join fetch s.driver
            where v.company.id = :companyId
              and s.startAt < :to and s.endAt > :from
            order by s.startAt
            """,
           countQuery = """
            select count(s) from VehicleShift s
            where s.vehicle.company.id = :companyId
              and s.startAt < :to and s.endAt > :from
            """)
    Page<VehicleShift> findForCompanyBetween(Long companyId, Instant from, Instant to, Pageable pageable);

    // Same, for one driver
    @Query(value = """
            select s from VehicleShift s
            join fetch s.vehicle v
            left join fetch s.driver d
            where v.company.id = :companyId
              and d.id = :driverUserId
              and s.startAt < :to and s.endAt > :from
            order by s.startAt
            """,
           countQuery = """
            select count(s) from VehicleShift s
            where s.vehicle.company.id = :companyId
              and s.driver.id = :driverUserId
              and s.startAt < :to and s.endAt > :from
            """)
    Page<VehicleShift> findForCompanyAndDriverBetween(Long companyId, Long driverUserId, Instant from, Instant to, Pageable pageable);

    // Overlap checks, mirroring the table's EXCLUDE constraints so the client gets a clear 409.
    // Two time ranges overlap when each one starts before the other ends.
    // ignoreShiftId: the shift being updated, so it doesn't count as overlapping itself.
    @Query("""
            select count(s) from VehicleShift s
            where s.vehicle.id = :vehicleId and s.id <> :ignoreShiftId
              and s.startAt < :endAt and s.endAt > :startAt
            """)
    long countVehicleOverlaps(Long vehicleId, Instant startAt, Instant endAt, Long ignoreShiftId);

    @Query("""
            select count(s) from VehicleShift s
            where s.driver.id = :driverUserId and s.id <> :ignoreShiftId
              and s.startAt < :endAt and s.endAt > :startAt
            """)
    long countDriverOverlaps(Long driverUserId, Instant startAt, Instant endAt, Long ignoreShiftId);
}
