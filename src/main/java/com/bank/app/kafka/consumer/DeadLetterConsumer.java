package com.bank.app.kafka.consumer;

import com.bank.app.entity.FailedEvent;
import com.bank.app.kafka.model.TransactionEvent;
import com.bank.app.repository.FailedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeadLetterConsumer {

    private final FailedEventRepository failedEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.transaction-dlt-topic:transaction-events-dlt}")
    private String dltTopic;

    @KafkaListener(
            topics = "${app.kafka.transaction-dlt-topic:transaction-events-dlt}",
            containerFactory = "dltKafkaListenerContainerFactory",
            groupId = "${app.kafka.consumer-groups.dlt:transaction-dlt-consumer-group}"
    )
    public void consume(TransactionEvent event) {
        if (event.getTraceId() != null) {
            MDC.put("traceId", event.getTraceId());
        }
        if (event.getTransactionId() != null) {
            MDC.put("transactionId", String.valueOf(event.getTransactionId()));
        }
        try {
            FailedEvent failedEvent = FailedEvent.builder()
                    .topic(dltTopic)
                    .eventType(event.getEventType())
                    .transactionId(event.getTransactionId())
                    .payload(toJson(event))
                    .errorMessage("Message moved to DLT after retries exhausted")
                    .build();

            failedEventRepository.save(failedEvent);
            log.error("action=kafka.dlt_saved transactionId={} topic={}", event.getTransactionId(), dltTopic);
        } finally {
            MDC.remove("traceId");
            MDC.remove("transactionId");
        }
    }

    private String toJson(TransactionEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize dead-letter event", ex);
        }
    }
}
