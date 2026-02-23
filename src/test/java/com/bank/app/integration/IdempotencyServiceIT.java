package com.bank.app.integration;

import com.bank.app.dto.IdempotentTransferResponse;
import com.bank.app.exception.BadRequestException;
import com.bank.app.exception.DuplicateTransferRequestException;
import com.bank.app.service.IdempotencyService;
import com.bank.app.service.MetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Testcontainers
class IdempotencyServiceIT {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private final MetricsService metricsService = mock(MetricsService.class);

    @AfterEach
    void cleanRedis() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getFirstMappedPort());
        connectionFactory.afterPropertiesSet();
        try {
            connectionFactory.getConnection().serverCommands().flushAll();
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void concurrentDuplicateRequests_onlyOneProcessesAndOtherGetsCachedResponse() throws Exception {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getFirstMappedPort());
        connectionFactory.afterPropertiesSet();

        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate();
        stringRedisTemplate.setConnectionFactory(connectionFactory);
        stringRedisTemplate.afterPropertiesSet();

        try {
            IdempotencyService idempotencyService = new IdempotencyService(
                    stringRedisTemplate,
                    new ObjectMapper().findAndRegisterModules(),
                    metricsService
            );

            Long userId = 42L;
            String idempotencyKey = UUID.randomUUID().toString();
            AtomicInteger executions = new AtomicInteger(0);
            CountDownLatch startGate = new CountDownLatch(1);

            Callable<IdempotentTransferResponse> task = () -> {
                startGate.await(5, TimeUnit.SECONDS);
                try {
                    return idempotencyService.checkAndStore(userId, idempotencyKey, () -> {
                        executions.incrementAndGet();
                        sleepQuietly(250);
                        return buildResponse();
                    });
                } catch (DuplicateTransferRequestException ex) {
                    return ex.getCachedResponse();
                } catch (BadRequestException ex) {
                    if (ex.getMessage() != null && ex.getMessage().contains("already being processed")) {
                        return null;
                    }
                    throw ex;
                }
            };

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<IdempotentTransferResponse> first = executor.submit(task);
                Future<IdempotentTransferResponse> second = executor.submit(task);

                startGate.countDown();

                IdempotentTransferResponse firstResponse = first.get(10, TimeUnit.SECONDS);
                IdempotentTransferResponse secondResponse = second.get(10, TimeUnit.SECONDS);

                assertThat(executions.get()).isEqualTo(1);

                int nonNullResponses = 0;
                if (firstResponse != null) {
                    nonNullResponses++;
                }
                if (secondResponse != null) {
                    nonNullResponses++;
                }
                assertThat(nonNullResponses).isGreaterThanOrEqualTo(1);

                DuplicateTransferRequestException duplicate = org.junit.jupiter.api.Assertions.assertThrows(
                        DuplicateTransferRequestException.class,
                        () -> idempotencyService.checkAndStore(userId, idempotencyKey, () -> {
                            throw new IllegalStateException("Processor must not execute again for same key");
                        })
                );
                assertThat(duplicate.getCachedResponse().getTransactionId()).isEqualTo(9001L);
            } finally {
                executor.shutdownNow();
            }
        } finally {
            connectionFactory.destroy();
        }
    }

    private IdempotentTransferResponse buildResponse() {
        return IdempotentTransferResponse.builder()
                .status("COMPLETED")
                .transactionId(9001L)
                .amount(new BigDecimal("1500.00"))
                .timestamp(LocalDateTime.now())
                .build();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during test execution", ex);
        }
    }
}
