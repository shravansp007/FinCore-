package com.bank.app.service;

import com.bank.app.config.JwtUtil;
import com.bank.app.dto.AuthRequest;
import com.bank.app.dto.AuthResponse;
import com.bank.app.entity.AuditEvent;
import com.bank.app.entity.User;
import com.bank.app.entity.UserDevice;
import com.bank.app.exception.ResourceNotFoundException;
import com.bank.app.exception.UnauthorizedException;
import com.bank.app.model.RefreshTokenSession;
import com.bank.app.repository.AuditEventRepository;
import com.bank.app.repository.UserDeviceRepository;
import com.bank.app.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String FAILED_ATTEMPTS_KEY_PREFIX = "failed_attempts:";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_SESSION_ID = "sid";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
    private static final String REDIS_SESSION_KEY_PREFIX = "refresh:session:";
    private static final String REDIS_USER_SESSIONS_KEY_PREFIX = "refresh:user:sessions:";
    private static final String REDIS_TOKEN_INDEX_KEY_PREFIX = "refresh:index:";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuthenticationManager authenticationManager;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    @Value("${security.cookie.secure:false}")
    private boolean secureCookie;

    @Value("${security.lockout-duration-minutes:15}")
    private long lockoutDurationMinutes;

    public AuthResponse login(AuthRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        String username = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        String failedAttemptsKey = FAILED_ATTEMPTS_KEY_PREFIX + username;

        Long failedAttempts = getFailedAttempts(failedAttemptsKey);
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            throw new UnauthorizedException(buildLockMessage(failedAttemptsKey));
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            long attemptsAfterFailure = registerFailedAttempt(failedAttemptsKey);
            if (attemptsAfterFailure >= MAX_FAILED_ATTEMPTS) {
                throw new UnauthorizedException(buildLockMessage(failedAttemptsKey));
            }
            throw ex;
        }

        stringRedisTemplate.delete(failedAttemptsKey);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String accessToken = jwtUtil.generateAccessToken(userDetails);
        if (!(userDetails instanceof User user)) {
            return new AuthResponse(accessToken);
        }

        issueRefreshToken(user, response);

        String userAgent = resolveUserAgent(httpRequest);
        String ipAddress = resolveIpAddress(httpRequest);
        String fingerprint = sha256(userAgent + "|" + ipAddress);
        boolean newDevice = upsertDeviceAndAudit(user, fingerprint, userAgent, ipAddress);

        return AuthResponse.builder()
                .token(accessToken)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .userId(user.getId())
                .newDevice(newDevice)
                .build();
    }

    public void unlockUserAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String username = user.getEmail() == null ? "" : user.getEmail().trim().toLowerCase();
        stringRedisTemplate.delete(FAILED_ATTEMPTS_KEY_PREFIX + username);
    }

    public void issueRefreshToken(User user, HttpServletResponse response) {
        String sessionId = UUID.randomUUID().toString();
        String refreshToken = jwtUtil.generateRefreshToken(user, sessionId);
        String refreshTokenHash = sha256(refreshToken);
        Duration ttl = Duration.ofMillis(refreshExpirationMs);

        RefreshTokenSession session = RefreshTokenSession.builder()
                .sessionId(sessionId)
                .userId(user.getId())
                .email(user.getEmail())
                .tokenHash(refreshTokenHash)
                .build();

        Map<String, String> redisSession = Map.of(
                "sessionId", session.getSessionId(),
                "userId", String.valueOf(session.getUserId()),
                "email", session.getEmail(),
                "tokenHash", session.getTokenHash()
        );

        String sessionKey = REDIS_SESSION_KEY_PREFIX + sessionId;
        String userSessionsKey = REDIS_USER_SESSIONS_KEY_PREFIX + user.getId();
        String indexKey = REDIS_TOKEN_INDEX_KEY_PREFIX + refreshTokenHash;

        stringRedisTemplate.opsForHash().putAll(sessionKey, redisSession);
        stringRedisTemplate.expire(sessionKey, ttl);
        stringRedisTemplate.opsForSet().add(userSessionsKey, sessionId);
        stringRedisTemplate.expire(userSessionsKey, ttl);
        stringRedisTemplate.opsForValue().set(indexKey, sessionId, ttl);

        response.addHeader("Set-Cookie", buildRefreshCookie(refreshToken, ttl).toString());
    }

    public AuthResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshTokenFromCookie(request);
        if (!StringUtils.hasText(refreshToken)) {
            throw new UnauthorizedException("Refresh token is missing");
        }

        String email;
        String sessionId;
        try {
            Map<String, Object> claims = jwtUtil.extractAllClaimsAsMap(refreshToken);
            String tokenType = (String) claims.get(CLAIM_TYPE);
            if (!TOKEN_TYPE_REFRESH.equals(tokenType)) {
                throw new UnauthorizedException("Invalid token type");
            }
            email = jwtUtil.extractUsername(refreshToken);
            sessionId = (String) claims.get(CLAIM_SESSION_ID);
            if (!StringUtils.hasText(sessionId)) {
                throw new UnauthorizedException("Refresh token is missing session claim");
            }
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            clearRefreshCookie(response);
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        String incomingTokenHash = sha256(refreshToken);
        String sessionKey = REDIS_SESSION_KEY_PREFIX + sessionId;
        Map<Object, Object> redisSession = stringRedisTemplate.opsForHash().entries(sessionKey);

        if (redisSession.isEmpty()) {
            clearRefreshCookie(response);
            throw new UnauthorizedException("Refresh session is invalid or expired");
        }

        String expectedTokenHash = (String) redisSession.get("tokenHash");
        String sessionUserId = (String) redisSession.get("userId");
        if (!String.valueOf(user.getId()).equals(sessionUserId)) {
            invalidateAllUserSessions(user.getId());
            clearRefreshCookie(response);
            throw new UnauthorizedException("Refresh session mismatch detected");
        }

        if (!incomingTokenHash.equals(expectedTokenHash)) {
            invalidateAllUserSessions(user.getId());
            clearRefreshCookie(response);
            throw new UnauthorizedException("Refresh token reuse detected. All sessions revoked.");
        }

        Duration ttl = Duration.ofMillis(refreshExpirationMs);
        String newRefreshToken = jwtUtil.generateRefreshToken(user, sessionId);
        String newRefreshHash = sha256(newRefreshToken);
        String userSessionsKey = REDIS_USER_SESSIONS_KEY_PREFIX + user.getId();
        String oldIndexKey = REDIS_TOKEN_INDEX_KEY_PREFIX + incomingTokenHash;
        String newIndexKey = REDIS_TOKEN_INDEX_KEY_PREFIX + newRefreshHash;

        stringRedisTemplate.opsForHash().put(sessionKey, "tokenHash", newRefreshHash);
        stringRedisTemplate.expire(sessionKey, ttl);
        stringRedisTemplate.opsForSet().add(userSessionsKey, sessionId);
        stringRedisTemplate.expire(userSessionsKey, ttl);
        stringRedisTemplate.delete(oldIndexKey);
        stringRedisTemplate.opsForValue().set(newIndexKey, sessionId, ttl);

        String accessToken = jwtUtil.generateAccessToken(user);
        response.addHeader("Set-Cookie", buildRefreshCookie(newRefreshToken, ttl).toString());

        return AuthResponse.builder()
                .token(accessToken)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .userId(user.getId())
                .newDevice(false)
                .build();
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshTokenFromCookie(request);
        if (StringUtils.hasText(refreshToken)) {
            try {
                Map<String, Object> claims = jwtUtil.extractAllClaimsAsMap(refreshToken);
                String sessionId = (String) claims.get(CLAIM_SESSION_ID);
                String email = jwtUtil.extractUsername(refreshToken);
                String tokenHash = sha256(refreshToken);

                if (StringUtils.hasText(email) && StringUtils.hasText(sessionId)) {
                    userRepository.findByEmail(email).ifPresent(user -> {
                        String sessionKey = REDIS_SESSION_KEY_PREFIX + sessionId;
                        String userSessionsKey = REDIS_USER_SESSIONS_KEY_PREFIX + user.getId();
                        String indexKey = REDIS_TOKEN_INDEX_KEY_PREFIX + tokenHash;

                        stringRedisTemplate.delete(sessionKey);
                        stringRedisTemplate.opsForSet().remove(userSessionsKey, sessionId);
                        stringRedisTemplate.delete(indexKey);
                    });
                }
            } catch (RuntimeException ignored) {
                // Cookie is always cleared even if token parsing fails
            }
        }

        clearRefreshCookie(response);
    }

    private boolean upsertDeviceAndAudit(User user, String deviceHash, String userAgent, String ipAddress) {
        Optional<UserDevice> existingDevice = userDeviceRepository.findByUserAndDeviceHash(user, deviceHash);
        LocalDateTime now = LocalDateTime.now();

        if (existingDevice.isPresent()) {
            UserDevice userDevice = existingDevice.get();
            userDevice.setLastSeen(now);
            userDeviceRepository.save(userDevice);
            return false;
        }

        UserDevice newDevice = UserDevice.builder()
                .user(user)
                .deviceHash(deviceHash)
                .firstSeen(now)
                .lastSeen(now)
                .trusted(true)
                .build();
        userDeviceRepository.save(newDevice);

        AuditEvent auditEvent = AuditEvent.builder()
                .user(user)
                .eventType(AuditEvent.AuditEventType.SUSPICIOUS_LOGIN)
                .message("New device login detected")
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .createdAt(now)
                .build();
        auditEventRepository.save(auditEvent);
        return true;
    }

    private String resolveUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (!StringUtils.hasText(userAgent)) {
            return "unknown";
        }
        return userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent;
    }

    private String resolveIpAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            String[] parts = forwardedFor.split(",");
            if (parts.length > 0 && StringUtils.hasText(parts[0])) {
                return parts[0].trim();
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    private long registerFailedAttempt(String failedAttemptsKey) {
        Long attempts = stringRedisTemplate.opsForValue().increment(failedAttemptsKey);
        if (attempts == null) {
            attempts = 1L;
        }
        stringRedisTemplate.expire(failedAttemptsKey, Duration.ofMinutes(lockoutDurationMinutes));
        return attempts;
    }

    private long getFailedAttempts(String failedAttemptsKey) {
        String raw = stringRedisTemplate.opsForValue().get(failedAttemptsKey);
        if (!StringUtils.hasText(raw)) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            stringRedisTemplate.delete(failedAttemptsKey);
            return 0L;
        }
    }

    private String buildLockMessage(String failedAttemptsKey) {
        Long ttlSeconds = stringRedisTemplate.getExpire(failedAttemptsKey);
        if (ttlSeconds == null || ttlSeconds < 0) {
            ttlSeconds = Duration.ofMinutes(lockoutDurationMinutes).toSeconds();
        }
        long minutesRemaining = (ttlSeconds + 59) / 60;
        if (minutesRemaining <= 0) {
            minutesRemaining = 1;
        }
        return "Account locked. Try again in " + minutesRemaining + " minutes";
    }

    private void invalidateAllUserSessions(Long userId) {
        String userSessionsKey = REDIS_USER_SESSIONS_KEY_PREFIX + userId;
        Set<String> sessionIds = stringRedisTemplate.opsForSet().members(userSessionsKey);
        if (sessionIds != null) {
            for (String sessionId : sessionIds) {
                stringRedisTemplate.delete(REDIS_SESSION_KEY_PREFIX + sessionId);
            }
        }
        stringRedisTemplate.delete(userSessionsKey);
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie buildRefreshCookie(String token, Duration ttl) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .sameSite("Strict")
                .maxAge(ttl)
                .build();
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cleared = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .sameSite("Strict")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader("Set-Cookie", cleared.toString());
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
