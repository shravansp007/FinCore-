package com.bank.app.kafka.consumer;

import com.bank.app.kafka.model.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final JavaMailSender mailSender;

    @KafkaListener(
            topics = "${app.kafka.transaction-topic:transaction-events}",
            groupId = "${app.kafka.consumer-groups.notification:notification-consumer-group}"
    )
    public void consume(TransactionEvent event) {
        withMdc(event);
        try {
            if (!StringUtils.hasText(event.getAccountHolderEmail())) {
                throw new IllegalStateException("Account holder email is missing for transaction " + event.getTransactionId());
            }

            String reference = StringUtils.hasText(event.getTransactionReference())
                    ? event.getTransactionReference()
                    : String.valueOf(event.getTransactionId());

            String body = "Your account " + event.getAccountNumber()
                    + " has been debited ₹" + event.getAmount()
                    + ". Reference: " + reference;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(event.getAccountHolderEmail());
            message.setSubject("FinCore Transaction Alert");
            message.setText(body);

            mailSender.send(message);
            log.info("action=kafka.notification_sent transactionId={} to={}", event.getTransactionId(), event.getAccountHolderEmail());
        } finally {
            clearMdc();
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
