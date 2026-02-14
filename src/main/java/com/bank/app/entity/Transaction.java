package com.bank.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id")
    private Account destinationAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime transactionDate;

    // ========================
    // Relation with User
    // ========================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @PrePersist
    protected void onCreate() {
        if (transactionDate == null) {
            transactionDate = LocalDateTime.now();
        }
        if (status == null) {
            status = TransactionStatus.COMPLETED;
        }
    }

    public enum TransactionType {
        DEPOSIT,
        WITHDRAW,
        WITHDRAWAL,
        TRANSFER,
        PAYMENT,
        BILL_PAYMENT,
        UNKNOWN;

        public boolean isWithdrawal() {
            return this == WITHDRAW || this == WITHDRAWAL;
        }

        public boolean isPayment() {
            return this == PAYMENT || this == BILL_PAYMENT;
        }

        public static TransactionType safeValueOf(String value) {
            if (value == null || value.isBlank()) {
                return UNKNOWN;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if ("WITHDRAWAL".equals(normalized)) {
                return WITHDRAWAL;
            }
            if ("WITHDRAW".equals(normalized)) {
                return WITHDRAW;
            }
            if ("PAYMENT".equals(normalized)) {
                return PAYMENT;
            }
            if ("BILL_PAYMENT".equals(normalized)) {
                return BILL_PAYMENT;
            }
            try {
                return TransactionType.valueOf(normalized);
            } catch (IllegalArgumentException ex) {
                return UNKNOWN;
            }
        }
    }

    public enum TransactionStatus {
        PENDING,
        COMPLETED,
        FAILED
    }
}

