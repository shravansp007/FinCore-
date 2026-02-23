package com.bank.app.service;

import com.bank.app.entity.Account;
import com.bank.app.repository.AccountRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MeterRegistry meterRegistry;
    private final AccountRepository accountRepository;

    private final ConcurrentMap<String, Counter> transactionCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> fraudAlertCounters = new ConcurrentHashMap<>();

    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    @jakarta.annotation.PostConstruct
    public void registerGauges() {
        Gauge.builder("fincore_active_accounts_total", accountRepository, repo -> {
                    try {
                        return (double) repo.countByAccountStatus(Account.AccountStatus.ACTIVE);
                    } catch (Exception ex) {
                        return 0.0d;
                    }
                })
                .description("Total non-dormant active accounts")
                .register(meterRegistry);

        Gauge.builder("fincore_cache_hit_ratio", this, service -> {
                    try {
                        return service.calculateCacheHitRatio();
                    } catch (Exception ex) {
                        return 0.0d;
                    }
                })
                .description("Redis cache hit ratio")
                .register(meterRegistry);
    }

    public Timer.Sample startTransferTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopTransferTimer(Timer.Sample sample) {
        sample.stop(Timer.builder("fincore_transfer_duration_seconds")
                .description("Time taken for full transfer processing")
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .register(meterRegistry));
    }

    public void incrementTransactionCounter(String type, String status) {
        String key = type + "|" + status;
        Counter counter = transactionCounters.computeIfAbsent(key, k ->
                Counter.builder("fincore_transactions_total")
                        .tags(Tags.of("type", type, "status", status))
                        .description("Total transactions by type and status")
                        .register(meterRegistry));
        counter.increment();
    }

    public void incrementFraudAlertsCounter(int riskScore) {
        String riskLevel = resolveRiskLevel(riskScore);
        Counter counter = fraudAlertCounters.computeIfAbsent(riskLevel, key ->
                Counter.builder("fincore_fraud_alerts_total")
                        .tags(Tags.of("risk_level", riskLevel))
                        .description("Fraud alerts by risk level")
                        .register(meterRegistry));
        counter.increment();
    }

    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    private double calculateCacheHitRatio() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        if (total == 0) {
            return 0.0d;
        }
        return (double) hits / total;
    }

    private String resolveRiskLevel(int riskScore) {
        if (riskScore >= 80) {
            return "HIGH";
        }
        if (riskScore >= 50) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
