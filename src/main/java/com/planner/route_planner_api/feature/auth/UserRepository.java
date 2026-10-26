package com.planner.route_planner_api.feature.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);          // SELECT … WHERE email = ? (returns true/false)

    Optional<User> findByEmail(String email);     // you'll need this for login

}