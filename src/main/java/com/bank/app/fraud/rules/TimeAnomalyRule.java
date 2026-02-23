package com.bank.app.fraud.rules;

import com.bank.app.fraud.FraudContext;
import com.bank.app.fraud.FraudResult;
import com.bank.app.fraud.FraudRule;
import com.bank.app.kafka.model.TransactionEvent;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
@Order(4)
public class TimeAnomalyRule implements FraudRule {

    private static final String RULE_NAME = "TimeAnomalyRule";
    private static final int RISK_SCORE = 15;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Override
    public FraudResult evaluate(TransactionEvent transaction, FraudContext context) {
        LocalDateTime timestamp = transaction.getTimestamp();
        boolean triggered = false;
        if (timestamp != null) {
            ZonedDateTime istTime = timestamp.atZone(ZoneId.systemDefault()).withZoneSameInstant(IST);
            int hour = istTime.getHour();
            triggered = hour >= 1 && hour < 5;
        }

        return FraudResult.builder()
                .flag(triggered)
                .ruleName(RULE_NAME)
                .riskScore(triggered ? RISK_SCORE : 0)
                .reason(triggered
                        ? "Transaction executed between 1AM and 5AM IST"
                        : "Transaction timing is within normal window")
                .build();
    }
}
