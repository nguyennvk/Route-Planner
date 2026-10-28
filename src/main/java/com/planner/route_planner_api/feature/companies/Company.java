package com.planner.route_planner_api.feature.companies;

import com.planner.route_planner_api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "companies")
@Getter 
public class Company extends BaseEntity{
    @Column(nullable = false, length = 254)
    private String name;

    @Column(nullable = false)
    private String timezone;

    protected Company() {}

    public Company(String name, String timezone){
        this.name = name;
        this.timezone = timezone;
    }
}
