package com.planner.route_planner_api.feature.depots;

import com.planner.route_planner_api.feature.companies.Company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

// A warehouse or yard where vehicles start and end their routes.
// Not a BaseEntity: the depots table has no created_at column.
@Entity
@Table(name = "depots")
@Getter
public class Depot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many depots belong to one company
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 254)
    private String name;

    // Plain id for now; becomes @ManyToOne Address once the Address entity exists
    @Column(name = "address_id", nullable = false)
    private Long addressId;

    protected Depot() {}

    public Depot(Company company, String name, Long addressId) {
        this.company = company;
        this.name = name;
        this.addressId = addressId;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeAddress(Long addressId) {
        this.addressId = addressId;
    }
}
