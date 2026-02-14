package com.bank.app.controller;

import com.bank.app.config.JwtUtil;
import com.bank.app.dto.AuthRequest;
import com.bank.app.dto.AuthResponse;
import com.bank.app.dto.RegisterRequest;
import com.bank.app.entity.User;
import com.bank.app.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserService userService;

    // ========================
    // REGISTER
    // ========================
    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    // ========================
    // LOGIN
    // ========================
    @PostMapping("/login")
    public AuthResponse login(@RequestBody AuthRequest request) {

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.getEmail(),
                                request.getPassword()
                        )
                );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtUtil.generateToken(userDetails);

        if (userDetails instanceof User user) {
            return AuthResponse.builder()
                    .token(token)
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .role(user.getRole() != null ? user.getRole().name() : null)
                    .userId(user.getId())
                    .build();
        }
        return new AuthResponse(token);
    }
}
