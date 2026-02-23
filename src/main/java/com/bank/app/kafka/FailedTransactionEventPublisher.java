package com.bank.app.kafka;

import com.bank.app.kafka.model.FailedTransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FailedTransactionEventPublisher {

    private final KafkaTemplate<String, Object> genericEventKafkaTemplate;

    @Value("${app.kafka.failed-transaction-topic:failed-transaction-events}")
    private String failedTransactionTopic;

    public void publish(FailedTransactionEvent event) {
        String key = event.getTransactionId() != null ? String.valueOf(event.getTransactionId()) : "unknown";
        genericEventKafkaTemplate.send(failedTransactionTopic, key, event);
        log.info("action=kafka.failed_tx_event_published topic={} transactionId={}", failedTransactionTopic, event.getTransactionId());
    }
}
