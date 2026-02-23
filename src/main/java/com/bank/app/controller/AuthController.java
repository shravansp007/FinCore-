package com.bank.app.controller;

import com.bank.app.dto.AuthRequest;
import com.bank.app.dto.AuthResponse;
import com.bank.app.dto.ForgotPasswordRequest;
import com.bank.app.dto.RegisterRequest;
import com.bank.app.dto.ResetPasswordRequest;
import com.bank.app.service.AuthService;
import com.bank.app.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and session lifecycle endpoints")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    @Operation(summary = "Register user", description = "Registers a new user and returns JWT payload")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registered successfully", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed")
    })
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @Operation(summary = "Login", description = "Authenticates user and issues access token and refresh cookie")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials or account lockout")
    })
    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        return authService.login(request, httpRequest, response);
    }

    @Operation(summary = "Forgot password", description = "Initiates password reset flow")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reset email dispatched"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping("/forgot-password")
    public Map<String, Object> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return userService.forgotPassword(request);
    }

    @Operation(summary = "Reset password", description = "Resets password with token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset successful"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired token")
    })
    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return userService.resetPassword(request);
    }

    @Operation(summary = "Refresh access token", description = "Rotates refresh token and returns new access token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token rotated", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "403", description = "Refresh token invalid")
    })
    @PostMapping("/refresh")
    public AuthResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        return authService.refreshToken(request, response);
    }

    @Operation(summary = "Logout", description = "Clears refresh token cookie and invalidates server-side session")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logged out successfully")
    })
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @Parameter(description = "HTTP request carrying refresh cookie") HttpServletRequest request,
            HttpServletResponse response
    ) {
        authService.logout(request, response);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
