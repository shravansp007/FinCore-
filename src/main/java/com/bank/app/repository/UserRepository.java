package com.bank.app.repository;

import com.bank.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bank.app.entity.Role;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByRole(Role role);
}
