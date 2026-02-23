package com.bank.app.service;

import com.bank.app.entity.Account;
import com.bank.app.entity.FraudAlert;
import com.bank.app.entity.FraudRuleConfig;
import com.bank.app.entity.TransferHistory;
import com.bank.app.fraud.FraudContext;
import com.bank.app.fraud.FraudResult;
import com.bank.app.fraud.FraudRule;
import com.bank.app.fraud.rules.AmountThresholdRule;
import com.bank.app.fraud.rules.NewBeneficiaryRule;
import com.bank.app.fraud.rules.RapidSequentialRule;
import com.bank.app.fraud.rules.RoundAmountRule;
import com.bank.app.fraud.rules.TimeAnomalyRule;
import com.bank.app.fraud.rules.VelocityRule;
import com.bank.app.kafka.model.TransactionEvent;
import com.bank.app.repository.AccountRepository;
import com.bank.app.repository.FraudAlertRepository;
import com.bank.app.repository.FraudRuleConfigRepository;
import com.bank.app.repository.TransferHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock
    private FraudRuleConfigRepository fraudRuleConfigRepository;
    @Mock
    private FraudAlertRepository fraudAlertRepository;
    @Mock
    private TransferHistoryRepository transferHistoryRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private MetricsService metricsService;

    private FraudDetectionService fraudDetectionService;

    @BeforeEach
    void setUp() {
        List<FraudRule> rules = List.of(
                new VelocityRule(),
                new AmountThresholdRule(),
                new NewBeneficiaryRule(),
                new TimeAnomalyRule(),
                new RoundAmountRule(),
                new RapidSequentialRule()
        );
        fraudDetectionService = new FraudDetectionService(
                rules,
                fraudRuleConfigRepository,
                fraudAlertRepository,
                transferHistoryRepository,
                accountRepository,
                metricsService
        );
    }

    @Test
    void rules_velocityRule_triggersWhenCountMoreThanFive() {
        FraudContext context = FraudContext.builder().velocityCountLast10Minutes(6).build();
        FraudResult result = new VelocityRule().evaluate(buildEvent("1000.00"), context);
        assertThat(result.isFlag()).isTrue();
        assertThat(result.getRiskScore()).isEqualTo(30);
    }

    @Test
    void rules_amountThresholdRule_triggersWhenAmountExceedsThreshold() {
        FraudContext context = FraudContext.builder().amountThreshold(new BigDecimal("50000")).build();
        FraudResult result = new AmountThresholdRule().evaluate(buildEvent("60000.00"), context);
        assertThat(result.isFlag()).isTrue();
        assertThat(result.getRiskScore()).isEqualTo(25);
    }

    @Test
    void rules_newBeneficiaryRule_triggersForFirstTransfer() {
        FraudContext context = FraudContext.builder().beneficiarySeenBefore(false).build();
        FraudResult result = new NewBeneficiaryRule().evaluate(buildEvent("1000.00"), context);
        assertThat(result.isFlag()).isTrue();
        assertThat(result.getRiskScore()).isEqualTo(20);
    }

    @Test
    void rules_timeAnomalyRule_triggersBetween1And5Ist() {
        TransactionEvent event = buildEvent("1000.00");
        LocalDateTime systemTimeEquivalent = LocalDateTime.of(2026, 1, 10, 2, 30)
                .atZone(ZoneId.of("Asia/Kolkata"))
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
        event.setTimestamp(systemTimeEquivalent);
        FraudResult result = new TimeAnomalyRule().evaluate(event, FraudContext.builder().build());
        assertThat(result.isFlag()).isTrue();
        assertThat(result.getRiskScore()).isEqualTo(15);
    }

    @Test
    void rules_roundAmountRule_triggersForStructuredAmounts() {
        FraudResult result = new RoundAmountRule().evaluate(buildEvent("50000.00"), FraudContext.builder().build());
        assertThat(result.isFlag()).isTrue();
        assertThat(result.getRiskScore()).isEqualTo(10);
    }

    @Test
    void rules_rapidSequentialRule_triggersForThreeDestinations() {
        FraudContext context = FraudContext.builder().distinctDestinationCountLast30Minutes(3).build();
        FraudResult result = new RapidSequentialRule().evaluate(buildEvent("1000.00"), context);
        assertThat(result.isFlag()).isTrue();
        assertThat(result.getRiskScore()).isEqualTo(25);
    }

    @Test
    void evaluate_scoreAggregation_createsFraudAlertAtOrAboveFifty() {
        when(fraudRuleConfigRepository.findByRuleName(FraudRuleConfig.AMOUNT_THRESHOLD_RULE))
                .thenReturn(Optional.of(FraudRuleConfig.builder()
                        .ruleName(FraudRuleConfig.AMOUNT_THRESHOLD_RULE)
                        .thresholdValue(new BigDecimal("50000"))
                        .build()));
        when(transferHistoryRepository.countBySourceAccountSince(any(), any())).thenReturn(6L); // velocity
        when(transferHistoryRepository.findRecentBySourceAccountSince(any(), any())).thenReturn(List.of());
        when(transferHistoryRepository.existsBySourceAccountIdAndDestinationAccountId(any(), any())).thenReturn(false); // new beneficiary

        TransactionEvent event = buildEvent("70000.00"); // amount rule
        LocalDateTime systemTimeEquivalent = LocalDateTime.of(2026, 1, 10, 2, 30)
                .atZone(ZoneId.of("Asia/Kolkata"))
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
        event.setTimestamp(systemTimeEquivalent); // time anomaly

        fraudDetectionService.evaluate(event);

        ArgumentCaptor<FraudAlert> captor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(fraudAlertRepository).save(captor.capture());
        assertThat(captor.getValue().getRiskScore()).isEqualTo(100); // capped
        assertThat(captor.getValue().getTriggeredRules()).contains("VelocityRule");
        assertThat(captor.getValue().getTriggeredRules()).contains("AmountThresholdRule");
        verify(metricsService).incrementFraudAlertsCounter(100);
    }

    @Test
    void evaluate_scoreAtLeastEighty_locksAccount() {
        when(fraudRuleConfigRepository.findByRuleName(FraudRuleConfig.AMOUNT_THRESHOLD_RULE))
                .thenReturn(Optional.of(FraudRuleConfig.builder()
                        .ruleName(FraudRuleConfig.AMOUNT_THRESHOLD_RULE)
                        .thresholdValue(new BigDecimal("50000"))
                        .build()));
        when(transferHistoryRepository.countBySourceAccountSince(any(), any())).thenReturn(10L);
        when(transferHistoryRepository.findRecentBySourceAccountSince(any(), any())).thenReturn(
                List.of(
                        TransferHistory.builder().destinationAccountId(10L).build(),
                        TransferHistory.builder().destinationAccountId(11L).build()
                )
        );
        when(transferHistoryRepository.existsBySourceAccountIdAndDestinationAccountId(any(), any())).thenReturn(false);

        Account account = Account.builder()
                .id(1L)
                .accountNumber("123")
                .active(true)
                .accountStatus(Account.AccountStatus.ACTIVE)
                .balance(new BigDecimal("100000"))
                .accountType(Account.AccountType.SAVINGS)
                .user(com.bank.app.entity.User.builder().id(1L).email("x@y.com").build())
                .build();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        fraudDetectionService.evaluate(buildEvent("80000.00"));

        assertThat(account.getActive()).isFalse();
        verify(accountRepository).save(account);
    }

    private TransactionEvent buildEvent(String amount) {
        return TransactionEvent.builder()
                .transactionId(101L)
                .accountId(1L)
                .amount(new BigDecimal(amount))
                .timestamp(LocalDateTime.now())
                .metadata(Map.of("destinationAccountId", 2L))
                .build();
    }
}
