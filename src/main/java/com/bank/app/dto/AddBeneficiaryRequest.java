package com.bank.app.dto;

import com.bank.app.entity.Beneficiary.TransferMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddBeneficiaryRequest {
    @NotBlank(message = "Nickname is required")
    private String nickname;

    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Account number must be 10 digits")
    private String accountNumber;

    @NotNull(message = "Transfer mode is required (IMPS, NEFT, RTGS)")
    private TransferMode transferMode;
}
