package com.bank.app.config;

import com.bank.app.entity.Role;
import com.bank.app.entity.User;
import com.bank.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.seed-enabled:false}")
    private boolean seedEnabled;

    @Value("${app.admin.seed-email:admin@bank.com}")
    private String seedEmail;

    @Value("${app.admin.seed-password:Admin@123}")
    private String seedPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled || userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (userRepository.existsByEmail(seedEmail)) {
            return;
        }
        User admin = User.builder()
                .firstName("Admin")
                .lastName("User")
                .email(seedEmail)
                .password(passwordEncoder.encode(seedPassword))
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        userRepository.save(admin);
    }
}
