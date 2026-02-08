package com.bank.app.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.issuer:banking-app}")
    private String issuer;

    @Value("${jwt.audience:banking-app-clients}")
    private String audience;

    @Value("${jwt.clock-skew-seconds:60}")
    private Long clockSkewSeconds;

    @PostConstruct
    void validateConfig() {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("JWT secret must be set");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes (256 bits)");
        }
        if (expiration == null || expiration <= 0) {
            throw new IllegalStateException("JWT expiration must be positive");
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .clockSkewSeconds(clockSkewSeconds)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Boolean isTokenExpired(String token) {
        Date expiration = extractExpiration(token);
        if (expiration == null) {
            return true;
        }
        long skewMillis = (clockSkewSeconds != null ? clockSkewSeconds : 0L) * 1000L;
        return expiration.before(new Date(System.currentTimeMillis() - skewMillis));
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        if (!userDetails.getAuthorities().isEmpty()) {
            claims.put("role", userDetails.getAuthorities().iterator().next().getAuthority());
        }
        return createToken(claims, userDetails.getUsername());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final Claims claims = extractAllClaims(token);
        final String username = claims.getSubject();
        final String tokenIssuer = claims.getIssuer();
        final Object audClaim = claims.get("aud");
        final Date exp = claims.getExpiration();
        if (username == null || username.isBlank() || exp == null) {
            return false;
        }
        if (!userDetails.getUsername().equals(username)) {
            return false;
        }
        if (tokenIssuer == null || !tokenIssuer.equals(issuer)) {
            return false;
        }
        if (!isAudienceValid(audClaim)) {
            return false;
        }
        return !isTokenExpired(token);
    }

    private boolean isAudienceValid(Object audClaim) {
        if (audClaim == null) {
            return false;
        }
        if (audClaim instanceof String audString) {
            return audience.equals(audString);
        }
        if (audClaim instanceof Iterable<?> audList) {
            for (Object item : audList) {
                if (audience.equals(String.valueOf(item))) {
                    return true;
                }
            }
            return false;
        }
        return audience.equals(String.valueOf(audClaim));
    }
}
