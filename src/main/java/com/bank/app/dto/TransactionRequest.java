package com.bank.app.dto;

import com.bank.app.entity.Transaction.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {
    
    @NotNull(message = "Transaction type is required")
    private TransactionType type;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 17, fraction = 2, message = "Amount must have at most 2 decimal places")
    private BigDecimal amount;
    
    @NotNull(message = "Source account ID is required")
    @Positive(message = "Source account ID must be positive")
    private Long sourceAccountId;
    
    /** Use either destinationAccountId or beneficiaryId for transfer (Yono-style) */
    @Positive(message = "Destination account ID must be positive")
    private Long destinationAccountId;

    @Positive(message = "Beneficiary ID must be positive")
    private Long beneficiaryId;

    @Pattern(regexp = "^\\d{10}$", message = "Destination account number must be 10 digits")
    private String destinationAccountNumber;

    private String description;
}
