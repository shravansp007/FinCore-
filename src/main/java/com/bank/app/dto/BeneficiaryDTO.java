package com.bank.app.dto;

import com.bank.app.entity.Beneficiary.TransferMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BeneficiaryDTO {
    private Long id;
    private String nickname;
    private String accountNumber;
    private String accountHolderName;
    private TransferMode transferMode;
}
