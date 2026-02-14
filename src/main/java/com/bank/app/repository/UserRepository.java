package com.bank.app.repository;

import com.bank.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bank.app.entity.Role;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);
}
