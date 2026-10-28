package com.planner.route_planner_api.feature.companies;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.planner.route_planner_api.feature.auth.User;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;

// Not a BaseEntity: this table has a two-column key and no id/created_at columns
@Entity
@Table(name = "company_members")
@Getter
public class CompanyMember {

    @EmbeddedId
    private CompanyMemberId id;

    @MapsId("companyId")                                  // fills id.companyId from this relationship
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @MapsId("userId")                                     // fills id.userId from this relationship
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)                    // a real Postgres ENUM, not varchar
    @Column(nullable = false, columnDefinition = "member_role")
    private MemberRole role;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected CompanyMember() {}

    public CompanyMember(Company company, User user, MemberRole role) {
        this.id = new CompanyMemberId(company.getId(), user.getId());
        this.company = company;
        this.user = user;
        this.role = role;
    }

    public void changeRole(MemberRole role) {
        this.role = role;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }
}
