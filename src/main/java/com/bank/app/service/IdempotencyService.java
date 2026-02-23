package com.bank.app.service;

import com.bank.app.dto.IdempotentTransferResponse;
import com.bank.app.exception.BadRequestException;
import com.bank.app.exception.DuplicateTransferRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final String LOCK_KEY_SUFFIX = ":lock";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final int WAIT_ITERATIONS = 50;
    private static final long WAIT_MS = 100L;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    public IdempotentTransferResponse checkAndStore(
            Long userId,
            String idempotencyKey,
            Supplier<IdempotentTransferResponse> processor
    ) {
        validateIdempotencyKey(idempotencyKey);

        String redisKey = buildKey(userId, idempotencyKey);
        String lockKey = redisKey + LOCK_KEY_SUFFIX;

        IdempotentTransferResponse cachedResponse = readCached(redisKey);
        if (cachedResponse != null) {
            metricsService.recordCacheHit();
            throw new DuplicateTransferRequestException(cachedResponse);
        }
        metricsService.recordCacheMiss();

        Boolean lockAcquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(lockAcquired)) {
            for (int i = 0; i < WAIT_ITERATIONS; i++) {
                sleepQuietly(WAIT_MS);
                cachedResponse = readCached(redisKey);
                if (cachedResponse != null) {
                    metricsService.recordCacheHit();
                    throw new DuplicateTransferRequestException(cachedResponse);
                }
            }
            throw new BadRequestException("Duplicate request with same idempotency key is already being processed");
        }

        try {
            cachedResponse = readCached(redisKey);
            if (cachedResponse != null) {
                metricsService.recordCacheHit();
                throw new DuplicateTransferRequestException(cachedResponse);
            }

            IdempotentTransferResponse freshResponse = processor.get();
            store(redisKey, freshResponse);
            return freshResponse;
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BadRequestException("X-Idempotency-Key header is required");
        }
        try {
            UUID.fromString(idempotencyKey);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("X-Idempotency-Key must be a valid UUID");
        }
    }

    private String buildKey(Long userId, String idempotencyKey) {
        return IDEMPOTENCY_KEY_PREFIX + userId + ":" + idempotencyKey;
    }

    private IdempotentTransferResponse readCached(String redisKey) {
        String raw = stringRedisTemplate.opsForValue().get(redisKey);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, IdempotentTransferResponse.class);
        } catch (JsonProcessingException ex) {
            stringRedisTemplate.delete(redisKey);
            return null;
        }
    }

    private void store(String redisKey, IdempotentTransferResponse response) {
        try {
            String raw = objectMapper.writeValueAsString(response);
            stringRedisTemplate.opsForValue().set(redisKey, raw, IDEMPOTENCY_TTL);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to cache idempotent transfer response", ex);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for idempotent transfer result", ex);
        }
    }
}
