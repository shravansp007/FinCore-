package com.bank.app.kafka.consumer;

import com.bank.app.entity.AuditLog;
import com.bank.app.kafka.model.TransactionEvent;
import com.bank.app.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditConsumer {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.transaction-topic:transaction-events}",
            groupId = "${app.kafka.consumer-groups.audit:audit-consumer-group}"
    )
    public void consume(TransactionEvent event) {
        withMdc(event);
        try {
            String metadataJson = toJson(event.getMetadata());

            AuditLog logEntry = AuditLog.builder()
                    .eventType(event.getEventType())
                    .accountId(event.getAccountId())
                    .amount(event.getAmount())
                    .performedBy(event.getPerformedBy())
                    .ipAddress(event.getIpAddress())
                    .timestamp(event.getTimestamp())
                    .metadata(metadataJson)
                    .build();

            auditLogRepository.save(logEntry);
            log.info("action=kafka.audit_saved transactionId={} accountId={}", event.getTransactionId(), event.getAccountId());
        } finally {
            clearMdc();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize audit metadata", ex);
        }
    }

    private void withMdc(TransactionEvent event) {
        if (event.getTraceId() != null) {
            MDC.put("traceId", event.getTraceId());
        }
        if (event.getTransactionId() != null) {
            MDC.put("transactionId", String.valueOf(event.getTransactionId()));
        }
    }

    private void clearMdc() {
        MDC.remove("traceId");
        MDC.remove("transactionId");
    }
}
