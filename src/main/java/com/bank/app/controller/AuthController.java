package com.bank.app.controller;

import com.bank.app.dto.AuthRequest;
import com.bank.app.dto.AuthResponse;
import com.bank.app.dto.ForgotPasswordRequest;
import com.bank.app.dto.RegisterRequest;
import com.bank.app.dto.ResetPasswordRequest;
import com.bank.app.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(userService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(userService.authenticate(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String token = userService.requestPasswordReset(request);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "If the email exists, a reset link has been sent");
        if (token != null) {
            response.put("resetToken", token);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Password reset successful");
        return ResponseEntity.ok(response);
    }
}
