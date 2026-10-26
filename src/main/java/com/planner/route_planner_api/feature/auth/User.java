package com.planner.route_planner_api.feature.auth;

import com.planner.route_planner_api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "users")
@Getter
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, columnDefinition = "citext")
    private String email;

    @Column(nullable = false, length = 254)
    private String passwordHash;

    @Column(length = 254)
    private String fullName;

    @Column()
    private String phone;

    protected User() {}

    public User(String email, String passwordHash, String fullName, String phone){
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
    }

}

