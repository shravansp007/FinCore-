package com.bank.app.config;

import com.bank.app.entity.User;
import com.bank.app.repository.UserRepository;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class LoggingFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final Tracer tracer;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            populateTraceMdc();
            populateUserMdc(request);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
            MDC.remove("transactionId");
            MDC.remove("traceId");
            MDC.remove("spanId");
        }
    }

    private void populateTraceMdc() {
        if (tracer.currentSpan() != null && tracer.currentSpan().context() != null) {
            MDC.put("traceId", tracer.currentSpan().context().traceId());
            MDC.put("spanId", tracer.currentSpan().context().spanId());
        }
    }

    private void populateUserMdc(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }

        String token = authHeader.substring(7);
        try {
            String email = jwtUtil.extractUsername(token);
            if (email == null) {
                return;
            }
            userRepository.findByEmail(email).map(User::getId).ifPresent(id -> MDC.put("userId", String.valueOf(id)));
        } catch (RuntimeException ignored) {
            // Do not block request when MDC extraction fails
        }
    }
}
