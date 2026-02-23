package com.bank.app.dto;

import com.bank.app.entity.FraudAlert;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlertReviewRequest {

    @NotNull(message = "status is required")
    private FraudAlert.FraudAlertStatus status;
}
