package com.bank.app.kafka;

import com.bank.app.kafka.model.DormantAccountEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountEventPublisher {

    private final KafkaTemplate<String, Object> genericEventKafkaTemplate;

    @Value("${app.kafka.account-topic:account-events}")
    private String accountTopic;

    public void publishDormantAccount(DormantAccountEvent event) {
        String key = event.getAccountId() != null ? String.valueOf(event.getAccountId()) : "unknown";
        genericEventKafkaTemplate.send(accountTopic, key, event);
        log.info("action=kafka.account_event_published topic={} accountId={}", accountTopic, event.getAccountId());
    }
}
