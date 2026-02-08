package com.bank.app.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "beneficiaries", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "account_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nickname;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferMode transferMode;

    public enum TransferMode {
        IMPS,
        NEFT,
        RTGS
    }
}
