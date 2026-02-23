package com.bank.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_devices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_device_hash", columnNames = {"user_id", "device_hash"})
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_hash", nullable = false, length = 128)
    private String deviceHash;

    @Column(name = "first_seen", nullable = false, updatable = false)
    private LocalDateTime firstSeen;

    @Column(name = "last_seen", nullable = false)
    private LocalDateTime lastSeen;

    @Column(name = "is_trusted", nullable = false)
    private Boolean trusted;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (firstSeen == null) {
            firstSeen = now;
        }
        if (lastSeen == null) {
            lastSeen = now;
        }
        if (trusted == null) {
            trusted = true;
        }
    }
}
