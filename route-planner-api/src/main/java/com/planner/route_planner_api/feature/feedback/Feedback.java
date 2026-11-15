package com.planner.route_planner_api.feature.feedback;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "feedback")
@Getter
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;

    @Column(nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)          // stored as plain text, checked by the table
    @Column(nullable = false)
    private PriceBand priceBand;

    @Column(precision = 10, scale = 2)
    private BigDecimal priceAmount;

    private Instant emailedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Feedback() {}

    public Feedback(String name, String email, String message, PriceBand priceBand, BigDecimal priceAmount) {
        this.name = name;
        this.email = email;
        this.message = message;
        this.priceBand = priceBand;
        this.priceAmount = priceAmount;
    }

    public void markEmailed() {
        this.emailedAt = Instant.now();
    }
}
