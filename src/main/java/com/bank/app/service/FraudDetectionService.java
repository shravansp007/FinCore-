package com.bank.app.service;

import com.bank.app.entity.Account;
import com.bank.app.entity.FraudAlert;
import com.bank.app.entity.FraudRuleConfig;
import com.bank.app.entity.TransferHistory;
import com.bank.app.fraud.FraudContext;
import com.bank.app.fraud.FraudResult;
import com.bank.app.fraud.FraudRule;
import com.bank.app.fraud.FraudRuleChain;
import com.bank.app.kafka.model.TransactionEvent;
import com.bank.app.repository.AccountRepository;
import com.bank.app.repository.FraudAlertRepository;
import com.bank.app.repository.FraudRuleConfigRepository;
import com.bank.app.repository.TransferHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {

    private static final BigDecimal DEFAULT_AMOUNT_THRESHOLD = new BigDecimal("50000");

    private final List<FraudRule> fraudRules;
    private final FraudRuleConfigRepository fraudRuleConfigRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final TransferHistoryRepository transferHistoryRepository;
    private final AccountRepository accountRepository;
    private final MetricsService metricsService;

    @Transactional
    public void evaluate(TransactionEvent event) {
        Long sourceAccountId = event.getAccountId();
        Long destinationAccountId = extractDestinationAccountId(event.getMetadata());
        if (sourceAccountId == null || destinationAccountId == null) {
            log.warn("action=fraud.skip transactionId={} reason=missing_account_ids", event.getTransactionId());
            return;
        }

        FraudContext context = buildContext(sourceAccountId, destinationAccountId);
        FraudRuleChain chain = buildChain();
        List<FraudResult> results = chain.evaluateAll(event, context);

        int totalRiskScore = Math.min(100, results.stream()
                .mapToInt(FraudResult::getRiskScore)
                .sum());

        List<FraudResult> flaggedResults = results.stream()
                .filter(FraudResult::isFlag)
                .toList();

        if (totalRiskScore >= 50) {
            createAlert(event, totalRiskScore, flaggedResults);
            metricsService.incrementFraudAlertsCounter(totalRiskScore);
        }
        if (totalRiskScore >= 80) {
            lockAccount(sourceAccountId);
        }

        transferHistoryRepository.save(TransferHistory.builder()
                .transactionId(event.getTransactionId())
                .sourceAccountId(sourceAccountId)
                .destinationAccountId(destinationAccountId)
                .amount(event.getAmount())
                .createdAt(event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now())
                .build());

        log.info("Fraud check triggered for transaction: {} with riskScore={}", event.getTransactionId(), totalRiskScore);
    }

    public BigDecimal getAmountThreshold() {
        return fraudRuleConfigRepository.findByRuleName(FraudRuleConfig.AMOUNT_THRESHOLD_RULE)
                .map(FraudRuleConfig::getThresholdValue)
                .orElse(DEFAULT_AMOUNT_THRESHOLD);
    }

    @Transactional
    public FraudRuleConfig updateAmountThreshold(BigDecimal thresholdValue) {
        FraudRuleConfig config = fraudRuleConfigRepository.findByRuleName(FraudRuleConfig.AMOUNT_THRESHOLD_RULE)
                .orElse(FraudRuleConfig.builder()
                        .ruleName(FraudRuleConfig.AMOUNT_THRESHOLD_RULE)
                        .build());
        config.setThresholdValue(thresholdValue);
        return fraudRuleConfigRepository.save(config);
    }

    private FraudContext buildContext(Long sourceAccountId, Long destinationAccountId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tenMinutesAgo = now.minusMinutes(10);
        LocalDateTime thirtyMinutesAgo = now.minusMinutes(30);

        long velocityCount = transferHistoryRepository.countBySourceAccountSince(sourceAccountId, tenMinutesAgo) + 1;
        List<TransferHistory> recentHistory = transferHistoryRepository.findRecentBySourceAccountSince(sourceAccountId, thirtyMinutesAgo);
        long distinctDestinationCount = buildDistinctDestinationCount(recentHistory, destinationAccountId);
        boolean beneficiarySeenBefore = transferHistoryRepository.existsBySourceAccountIdAndDestinationAccountId(
                sourceAccountId, destinationAccountId
        );

        return FraudContext.builder()
                .recentTransactionHistory(recentHistory)
                .velocityCountLast10Minutes(velocityCount)
                .distinctDestinationCountLast30Minutes(distinctDestinationCount)
                .beneficiarySeenBefore(beneficiarySeenBefore)
                .amountThreshold(getAmountThreshold())
                .build();
    }

    private long buildDistinctDestinationCount(List<TransferHistory> recentHistory, Long currentDestination) {
        Set<Long> destinations = recentHistory.stream()
                .map(TransferHistory::getDestinationAccountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        destinations.add(currentDestination);
        return destinations.size();
    }

    private FraudRuleChain buildChain() {
        List<FraudRule> ordered = new ArrayList<>(fraudRules);
        ordered.sort(AnnotationAwareOrderComparator.INSTANCE);
        return new FraudRuleChain(ordered);
    }

    private void createAlert(TransactionEvent event, int riskScore, List<FraudResult> flaggedResults) {
        String triggeredRules = flaggedResults.stream()
                .map(result -> result.getRuleName() + "(" + result.getRiskScore() + ")")
                .collect(Collectors.joining(","));

        FraudAlert alert = FraudAlert.builder()
                .transactionId(event.getTransactionId())
                .accountId(event.getAccountId())
                .riskScore(riskScore)
                .triggeredRules(triggeredRules.isBlank() ? "NONE" : triggeredRules)
                .status(FraudAlert.FraudAlertStatus.PENDING)
                .build();

        fraudAlertRepository.save(alert);
    }

    private void lockAccount(Long accountId) {
        accountRepository.findById(accountId).ifPresent(account -> {
            if (Boolean.TRUE.equals(account.getActive())) {
                account.setActive(false);
                accountRepository.save(account);
            }
        });
    }

    private Long extractDestinationAccountId(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get("destinationAccountId");
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
