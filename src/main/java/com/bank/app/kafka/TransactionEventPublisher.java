package com.bank.app.kafka;

import com.bank.app.kafka.model.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionEventPublisher {

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    @Value("${app.kafka.transaction-topic:transaction-events}")
    private String transactionTopic;

    public void publish(TransactionEvent event) {
        String key = event.getAccountId() != null ? String.valueOf(event.getAccountId()) : "unknown";
        kafkaTemplate.send(transactionTopic, key, event);
        log.info("action=kafka.publish topic={} key={} transactionId={}", transactionTopic, key, event.getTransactionId());
    }
}
