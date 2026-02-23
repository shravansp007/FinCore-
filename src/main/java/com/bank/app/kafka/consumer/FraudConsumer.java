package com.bank.app.kafka.consumer;

import com.bank.app.kafka.model.TransactionEvent;
import com.bank.app.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FraudConsumer {

    private final FraudDetectionService fraudDetectionService;

    @KafkaListener(
            topics = "${app.kafka.transaction-topic:transaction-events}",
            groupId = "${app.kafka.consumer-groups.fraud:fraud-consumer-group}"
    )
    public void consume(TransactionEvent event) {
        if (event.getTraceId() != null) {
            MDC.put("traceId", event.getTraceId());
        }
        if (event.getTransactionId() != null) {
            MDC.put("transactionId", String.valueOf(event.getTransactionId()));
        }
        try {
            fraudDetectionService.evaluate(event);
        } finally {
            MDC.remove("traceId");
            MDC.remove("transactionId");
        }
    }
}
