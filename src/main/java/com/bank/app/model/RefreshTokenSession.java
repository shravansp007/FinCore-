package com.bank.app.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenSession {
    private String sessionId;
    private Long userId;
    private String email;
    private String tokenHash;
}
