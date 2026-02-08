package com.bank.app.service;

import com.bank.app.config.JwtUtil;
import com.bank.app.dto.AuthResponse;
import com.bank.app.dto.RegisterRequest;
import com.bank.app.entity.Role;
import com.bank.app.entity.User;
import com.bank.app.repository.PasswordResetTokenRepository;
import com.bank.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private UserService userService;

    @Test
    void register_createsUserWithUserRole() {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Ava")
                .lastName("Kim")
                .email("ava@example.com")
                .password("password123")
                .phoneNumber("9999999999")
                .build();

        when(userRepository.existsByEmail("ava@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(5L);
            return u;
        });
        when(jwtUtil.generateToken(any(User.class))).thenReturn("token-123");

        AuthResponse response = userService.register(request);

        assertThat(response.getToken()).isEqualTo("token-123");
        assertThat(response.getRole()).isEqualTo(Role.USER.name());
        assertThat(response.getUserId()).isEqualTo(5L);
    }
}
