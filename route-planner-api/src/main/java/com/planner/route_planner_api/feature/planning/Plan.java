package com.planner.route_planner_api.feature.planning;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.planner.route_planner_api.common.error.ConflictException;
import com.planner.route_planner_api.common.persistence.BaseEntity;
import com.planner.route_planner_api.feature.auth.User;
import com.planner.route_planner_api.feature.companies.Company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity 
@Table(name = "plans")
@Getter 
public class Plan extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private LocalDate planDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)                 
    @Column(nullable = false, columnDefinition = "plan_status")
    private PlanStatus status = PlanStatus.draft;
    
    private Long hardScore;
    private Long mediumScore;
    private Long softScore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    private Instant solvedAt;
    private Instant publishedAt;

    protected Plan() {}
        public Plan(Company company, LocalDate planDate, User createdBy) {
        this.company = company;
        this.planDate = planDate;
        this.createdBy = createdBy;
    }

    public void startSolving() {
        this.status = PlanStatus.solving;
    }

    public void markSolved(long hardScore, long mediumScore, long softScore) {
        this.hardScore = hardScore;
        this.mediumScore = mediumScore;
        this.softScore = softScore;
        this.solvedAt = Instant.now();
        this.status = PlanStatus.draft; 
    }

    // Only a solved draft goes out to drivers: not one still solving, failed, or already published
    public void publish() {
        if (status != PlanStatus.draft || solvedAt == null) {
            throw new ConflictException("Only a solved draft plan can be published; this one is " + status);
        }
        this.status = PlanStatus.published;
        this.publishedAt = Instant.now();
    }

    public void complete() {
        this.status = PlanStatus.completed;
    }

    public void markFailed() {
        this.status = PlanStatus.failed;
    }

}
