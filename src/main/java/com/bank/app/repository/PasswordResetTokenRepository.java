package com.bank.app.repository;

import com.bank.app.entity.PasswordResetToken;
import com.bank.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    long deleteByUser(User user);
    long deleteByExpiresAtBefore(LocalDateTime time);
}
