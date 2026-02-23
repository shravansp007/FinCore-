package com.bank.app.config;

import com.bank.app.kafka.model.TransactionEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:fincore-group}")
    private String groupId;

    @Value("${app.kafka.transaction-dlt-topic:transaction-events-dlt}")
    private String transactionDltTopic;

    @Bean
    public ConsumerFactory<String, TransactionEvent> transactionEventConsumerFactory() {
        JsonDeserializer<TransactionEvent> valueDeserializer = new JsonDeserializer<>(TransactionEvent.class);
        valueDeserializer.addTrustedPackages("*");

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // Reduce connection retry noise when broker is unavailable.
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, 5000);
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, 30000);
        props.put(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, 5000);

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, TransactionEvent> transactionEventConsumerFactory,
            KafkaTemplate<String, TransactionEvent> transactionEventKafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, TransactionEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(transactionEventConsumerFactory);
        factory.setMissingTopicsFatal(false);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                transactionEventKafkaTemplate,
                (record, ex) -> new TopicPartition(transactionDltTopic, record.partition())
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionEvent> dltKafkaListenerContainerFactory(
            ConsumerFactory<String, TransactionEvent> transactionEventConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, TransactionEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(transactionEventConsumerFactory);
        factory.setMissingTopicsFatal(false);
        return factory;
    }
}
