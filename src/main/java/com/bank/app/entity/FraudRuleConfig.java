package com.bank.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fraud_rule_config", uniqueConstraints = {
        @UniqueConstraint(name = "uk_fraud_rule_config_rule_name", columnNames = "rule_name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudRuleConfig {

    public static final String AMOUNT_THRESHOLD_RULE = "AMOUNT_THRESHOLD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;

    @Column(name = "threshold_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal thresholdValue;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void onChange() {
        updatedAt = LocalDateTime.now();
    }
}
