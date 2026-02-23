package com.bank.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "fraud_alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlert {

    public enum FraudAlertStatus {
        PENDING,
        CLEARED,
        CONFIRMED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @Column(name = "triggered_rules", nullable = false, length = 1000)
    private String triggeredRules;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FraudAlertStatus status;

    @Column(name = "reviewed_by", length = 150)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = FraudAlertStatus.PENDING;
        }
    }
}
