package com.planner.route_planner_api.feature.customers;

import com.planner.route_planner_api.common.persistence.BaseEntity;
import com.planner.route_planner_api.feature.addresses.Address;
import com.planner.route_planner_api.feature.companies.Company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

// Someone a company delivers to
@Entity
@Table(name = "customers")
@Getter
public class Customer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 254)
    private String name;

    @Column
    private String phone;                   // E.164, e.g. +14165550123

    @Column(columnDefinition = "citext")    // case-insensitive, like users.email; unique per company
    private String email;

    // Where orders go when the order doesn't name an address; optional
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_address_id")
    private Address defaultAddress;

    protected Customer() {}

    public Customer(Company company, String name, String phone, String email, Address defaultAddress) {
        this.company = company;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.defaultAddress = defaultAddress;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changePhone(String phone) {
        this.phone = phone;
    }

    public void changeEmail(String email) {
        this.email = email;
    }

    // Existing orders keep their own address; only new orders without an addressId use this
    public void changeDefaultAddress(Address defaultAddress) {
        this.defaultAddress = defaultAddress;
    }
}
