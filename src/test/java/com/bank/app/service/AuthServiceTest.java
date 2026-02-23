package com.bank.app.service;

import com.bank.app.config.JwtUtil;
import com.bank.app.dto.AuthRequest;
import com.bank.app.dto.AuthResponse;
import com.bank.app.entity.AuditEvent;
import com.bank.app.entity.Role;
import com.bank.app.entity.User;
import com.bank.app.entity.UserDevice;
import com.bank.app.exception.UnauthorizedException;
import com.bank.app.repository.AuditEventRepository;
import com.bank.app.repository.UserDeviceRepository;
import com.bank.app.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserDeviceRepository userDeviceRepository;
    @Mock
    private AuditEventRepository auditEventRepository;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private SetOperations<String, String> setOperations;
    @Mock
    private Authentication authentication;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                jwtUtil,
                userRepository,
                userDeviceRepository,
                auditEventRepository,
                authenticationManager,
                stringRedisTemplate
        );

        ReflectionTestUtils.setField(authService, "refreshExpirationMs", 604800000L);
        ReflectionTestUtils.setField(authService, "secureCookie", false);
        ReflectionTestUtils.setField(authService, "lockoutDurationMinutes", 15L);

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    void login_successfulLogin_returnsTokenAndMarksNewDevice() {
        AuthRequest request = new AuthRequest();
        request.setEmail("user@fincore.com");
        request.setPassword("password");

        User user = buildUser();
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("User-Agent", "JUnit-Agent");
        httpRequest.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(valueOperations.get("failed_attempts:user@fincore.com")).thenReturn(null);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);
        when(jwtUtil.generateAccessToken(user)).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(eq(user), anyString())).thenReturn("refresh-token-1");
        when(userDeviceRepository.findByUserAndDeviceHash(eq(user), anyString())).thenReturn(Optional.empty());

        AuthResponse authResponse = authService.login(request, httpRequest, response);

        assertThat(authResponse.getToken()).isEqualTo("access-token");
        assertThat(authResponse.getNewDevice()).isTrue();
        assertThat(response.getHeaders("Set-Cookie")).isNotEmpty();
        assertThat(response.getHeaders("Set-Cookie").get(0)).contains("HttpOnly");

        verify(stringRedisTemplate).delete("failed_attempts:user@fincore.com");
        verify(auditEventRepository).save(any(AuditEvent.class));
    }

    @Test
    void login_failedLogin_incrementsRedisCounter() {
        AuthRequest request = new AuthRequest();
        request.setEmail("user@fincore.com");
        request.setPassword("wrong-password");

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(valueOperations.get("failed_attempts:user@fincore.com")).thenReturn(null);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Invalid credentials"));
        when(valueOperations.increment("failed_attempts:user@fincore.com")).thenReturn(1L);

        assertThrows(BadCredentialsException.class, () -> authService.login(request, httpRequest, response));

        verify(stringRedisTemplate).expire(eq("failed_attempts:user@fincore.com"), eq(Duration.ofMinutes(15)));
    }

    @Test
    void login_accountLockoutAtFiveAttempts_throwsUnauthorized() {
        AuthRequest request = new AuthRequest();
        request.setEmail("user@fincore.com");
        request.setPassword("any");

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(valueOperations.get("failed_attempts:user@fincore.com")).thenReturn("5");
        when(stringRedisTemplate.getExpire("failed_attempts:user@fincore.com")).thenReturn(600L);

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> authService.login(request, httpRequest, response)
        );

        assertThat(exception.getMessage()).isEqualTo("Account locked. Try again in 10 minutes");
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void refreshToken_rotation_replacesTokenAndIssuesNewAccessToken() {
        User user = buildUser();
        String oldRefreshToken = "old-refresh-token";
        String newRefreshToken = "new-refresh-token";
        String oldHash = sha256(oldRefreshToken);
        String newHash = sha256(newRefreshToken);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refresh_token", oldRefreshToken));
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        claims.put("sid", "sid-1");

        Map<Object, Object> redisSession = new HashMap<>();
        redisSession.put("sessionId", "sid-1");
        redisSession.put("userId", "1");
        redisSession.put("email", "user@fincore.com");
        redisSession.put("tokenHash", oldHash);

        when(jwtUtil.extractAllClaimsAsMap(oldRefreshToken)).thenReturn(claims);
        when(jwtUtil.extractUsername(oldRefreshToken)).thenReturn("user@fincore.com");
        when(userRepository.findByEmail("user@fincore.com")).thenReturn(Optional.of(user));
        when(hashOperations.entries("refresh:session:sid-1")).thenReturn(redisSession);
        when(jwtUtil.generateRefreshToken(user, "sid-1")).thenReturn(newRefreshToken);
        when(jwtUtil.generateAccessToken(user)).thenReturn("rotated-access-token");

        AuthResponse authResponse = authService.refreshToken(request, response);

        assertThat(authResponse.getToken()).isEqualTo("rotated-access-token");
        verify(hashOperations).put("refresh:session:sid-1", "tokenHash", newHash);
        verify(stringRedisTemplate).delete("refresh:index:" + oldHash);
        verify(valueOperations).set(eq("refresh:index:" + newHash), eq("sid-1"), any(Duration.class));
        assertThat(response.getHeaders("Set-Cookie")).anySatisfy(cookie ->
                assertThat(cookie).contains("refresh_token=" + newRefreshToken)
        );
    }

    @Test
    void refreshToken_reusedTokenDetected_invalidatesAllUserSessions() {
        User user = buildUser();
        String incomingToken = "incoming-token";
        String incomingHash = sha256(incomingToken);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refresh_token", incomingToken));
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        claims.put("sid", "sid-reused");

        Map<Object, Object> redisSession = new HashMap<>();
        redisSession.put("sessionId", "sid-reused");
        redisSession.put("userId", "1");
        redisSession.put("email", "user@fincore.com");
        redisSession.put("tokenHash", sha256("some-other-token"));

        when(jwtUtil.extractAllClaimsAsMap(incomingToken)).thenReturn(claims);
        when(jwtUtil.extractUsername(incomingToken)).thenReturn("user@fincore.com");
        when(userRepository.findByEmail("user@fincore.com")).thenReturn(Optional.of(user));
        when(hashOperations.entries("refresh:session:sid-reused")).thenReturn(redisSession);
        when(setOperations.members("refresh:user:sessions:1")).thenReturn(Set.of("sid-reused", "sid-other"));

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> authService.refreshToken(request, response)
        );

        assertThat(exception.getMessage()).contains("Refresh token reuse detected");
        verify(stringRedisTemplate).delete("refresh:session:sid-reused");
        verify(stringRedisTemplate).delete("refresh:session:sid-other");
        verify(stringRedisTemplate).delete("refresh:user:sessions:1");
        verify(stringRedisTemplate, never()).delete("refresh:index:" + incomingHash);
        assertThat(response.getHeaders("Set-Cookie")).anySatisfy(cookie ->
                assertThat(cookie).contains("Max-Age=0")
        );
    }

    private User buildUser() {
        return User.builder()
                .id(1L)
                .firstName("Fin")
                .lastName("Core")
                .email("user@fincore.com")
                .password("encoded")
                .role(Role.USER)
                .enabled(true)
                .build();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
