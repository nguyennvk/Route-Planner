package com.planner.route_planner_api.feature.customers;

import java.math.BigDecimal;

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

// A product in the company's catalogue (e.g. "20L water bottle"); orders are made of these.
// Not a BaseEntity: the items table has no created_at column.
@Entity
@Table(name = "items")
@Getter
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column
    private String sku;

    @Column(nullable = false, length = 254)
    private String name;

    // Weight of ONE unit; an order line weighs weightKg * quantity
    @Column(name = "weight_kg", nullable = false, precision = 10, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "volume_m3", precision = 10, scale = 4)
    private BigDecimal volumeM3;

    protected Item() {}

    public Item(Company company, String sku, String name, BigDecimal weightKg, BigDecimal volumeM3) {
        this.company = company;
        this.sku = sku;
        this.name = name;
        this.weightKg = weightKg;
        this.volumeM3 = volumeM3;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeSku(String sku) {
        this.sku = sku;
    }

    public void changeWeight(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public void changeVolume(BigDecimal volumeM3) {
        this.volumeM3 = volumeM3;
    }
}
