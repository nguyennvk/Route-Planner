package com.planner.route_planner_api.feature.companies;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

// The two-column primary key (company_id, user_id) of company_members
@Embeddable
@Getter
public class CompanyMemberId implements Serializable {

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "user_id")
    private Long userId;

    protected CompanyMemberId() {}

    public CompanyMemberId(Long companyId, Long userId) {
        this.companyId = companyId;
        this.userId = userId;
    }

    // Two keys are equal when both columns match; Hibernate relies on this
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompanyMemberId other)) return false;
        return Objects.equals(companyId, other.companyId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(companyId, userId);
    }
}
