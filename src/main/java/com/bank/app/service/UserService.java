package com.bank.app.service;

import com.bank.app.dto.*;
import com.bank.app.config.JwtUtil;
import com.bank.app.entity.Role;
import com.bank.app.entity.User;
import com.bank.app.exception.BadRequestException;
import com.bank.app.exception.ConflictException;
import com.bank.app.repository.PasswordResetTokenRepository;
import com.bank.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    @Value("${app.password-reset.token-ttl-minutes:15}")
    private int resetTokenTtlMinutes;

    @Value("${app.password-reset.dev-mode:true}")
    private boolean passwordResetDevMode;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered");
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .role(Role.USER)
                .enabled(true)
                .build();

        user = userRepository.save(user);
        log.info("action=user.register.success userId={}", user.getId());
        String token = jwtUtil.generateToken(user);

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .userId(user.getId())
                .build();
    }

    public AuthResponse authenticate(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String token = jwtUtil.generateToken(user);
        log.info("action=user.login.success userId={}", user.getId());

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .userId(user.getId())
                .build();
    }

    public User getCurrentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public ProfileResponse getProfile(String email) {
        User user = getCurrentUser(email);
        return ProfileResponse.builder()
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole().name())
                .build();
    }

    @Transactional
    public ProfileResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = getCurrentUser(email);
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        user = userRepository.save(user);
        return getProfile(email);
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = getCurrentUser(email);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public String requestPasswordReset(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return null;
        }
        log.info("action=user.password_reset.requested userId={}", user.getId());
        passwordResetTokenRepository.deleteByUser(user);
        String rawToken = UUID.randomUUID().toString().replace("-", "");
        String tokenHash = hashToken(rawToken);
        passwordResetTokenRepository.save(
                com.bank.app.entity.PasswordResetToken.builder()
                        .tokenHash(tokenHash)
                        .user(user)
                        .expiresAt(LocalDateTime.now().plusMinutes(resetTokenTtlMinutes))
                        .used(false)
                        .build()
        );
        if (!passwordResetDevMode) {
            return null;
        }
        // In dev mode, return the token so it can be used without email delivery.
        // In production, integrate an email service and remove this.
        return rawToken;
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = hashToken(request.getToken());
        var tokenOpt = passwordResetTokenRepository.findByTokenHash(tokenHash);
        if (tokenOpt.isEmpty()) {
            throw new BadRequestException("Invalid or expired reset token");
        }
        var token = tokenOpt.get();
        if (Boolean.TRUE.equals(token.getUsed()) || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Invalid or expired reset token");
        }
        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        token.setUsed(true);
        passwordResetTokenRepository.save(token);
        log.info("action=user.password_reset.success userId={}", user.getId());
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to hash token", e);
        }
    }
}
