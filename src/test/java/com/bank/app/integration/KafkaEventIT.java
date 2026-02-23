package com.bank.app.integration;

import com.bank.app.kafka.TransactionEventPublisher;
import com.bank.app.kafka.model.TransactionEvent;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class KafkaEventIT {

    private static final String TOPIC = "transaction-events";

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    private KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private ProducerFactory<String, TransactionEvent> producerFactory;
    private KafkaConsumer<String, TransactionEvent> consumer;

    @BeforeEach
    void setUp() throws Exception {
        String bootstrapServers = kafkaContainer.getBootstrapServers();

        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (AdminClient adminClient = AdminClient.create(adminProps)) {
            adminClient.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
        }

        Map<String, Object> producerProps = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class,
                ProducerConfig.ACKS_CONFIG, "1",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false,
                ProducerConfig.RETRIES_CONFIG, 0,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000
        );

        producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);
        kafkaTemplate.setDefaultTopic(TOPIC);

        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "kafka-event-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class,
                JsonDeserializer.TRUSTED_PACKAGES, "*"
        );

        JsonDeserializer<TransactionEvent> jsonDeserializer = new JsonDeserializer<>(TransactionEvent.class);
        jsonDeserializer.addTrustedPackages("*");
        consumer = new KafkaConsumer<>(consumerProps, new StringDeserializer(), jsonDeserializer);
        consumer.subscribe(List.of(TOPIC));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
        if (kafkaTemplate != null) {
            kafkaTemplate.destroy();
        }
        if (producerFactory instanceof DefaultKafkaProducerFactory<String, TransactionEvent> defaultFactory) {
            defaultFactory.destroy();
        }
    }

    @Test
    void publishTransactionEvent_realConsumerReceivesMessage() {
        TransactionEventPublisher publisher = new TransactionEventPublisher(kafkaTemplate);
        ReflectionTestUtils.setField(publisher, "transactionTopic", TOPIC);

        TransactionEvent event = TransactionEvent.builder()
                .eventType("TRANSFER_DEBIT")
                .transactionId(7001L)
                .transactionReference("TXN-KAFKA-7001")
                .accountId(99L)
                .accountNumber("99887766")
                .amount(new BigDecimal("2500.00"))
                .performedBy("user@fincore.com")
                .accountHolderEmail("user@fincore.com")
                .ipAddress("127.0.0.1")
                .timestamp(LocalDateTime.now())
                .traceId("trace-123")
                .metadata(Map.of("destinationAccountId", 100L))
                .build();

        publisher.publish(event);
        kafkaTemplate.flush();

        TransactionEvent received = pollForEvent();
        assertThat(received).isNotNull();
        assertThat(received.getTransactionId()).isEqualTo(7001L);
        assertThat(received.getAccountId()).isEqualTo(99L);
        assertThat(received.getTraceId()).isEqualTo("trace-123");
    }

    private TransactionEvent pollForEvent() {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, TransactionEvent> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, TransactionEvent> record : records) {
                if (record.value() != null && Long.valueOf(7001L).equals(record.value().getTransactionId())) {
                    return record.value();
                }
            }
        }
        return null;
    }
}
