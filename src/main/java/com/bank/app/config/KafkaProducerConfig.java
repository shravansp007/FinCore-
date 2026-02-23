package com.bank.app.config;

import com.bank.app.kafka.model.TransactionEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.transaction-topic:transaction-events}")
    private String transactionTopic;

    @Value("${app.kafka.transaction-dlt-topic:transaction-events-dlt}")
    private String transactionDltTopic;

    @Value("${app.kafka.account-topic:account-events}")
    private String accountTopic;

    @Value("${app.kafka.failed-transaction-topic:failed-transaction-events}")
    private String failedTransactionTopic;

    @Bean
    public ProducerFactory<String, TransactionEvent> transactionEventProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, TransactionEvent> transactionEventKafkaTemplate(
            ProducerFactory<String, TransactionEvent> transactionEventProducerFactory
    ) {
        return new KafkaTemplate<>(transactionEventProducerFactory);
    }

    @Bean
    public ProducerFactory<String, Object> genericEventProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> genericEventKafkaTemplate(
            ProducerFactory<String, Object> genericEventProducerFactory
    ) {
        return new KafkaTemplate<>(genericEventProducerFactory);
    }

    @Bean
    public NewTopic transactionEventsTopic() {
        return TopicBuilder.name(transactionTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic transactionEventsDltTopic() {
        return TopicBuilder.name(transactionDltTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic accountEventsTopic() {
        return TopicBuilder.name(accountTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic failedTransactionEventsTopic() {
        return TopicBuilder.name(failedTransactionTopic).partitions(3).replicas(1).build();
    }
}
