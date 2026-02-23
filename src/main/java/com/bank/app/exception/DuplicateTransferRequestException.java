package com.bank.app.exception;

import com.bank.app.dto.IdempotentTransferResponse;
import lombok.Getter;

@Getter
public class DuplicateTransferRequestException extends RuntimeException {
    private final IdempotentTransferResponse cachedResponse;

    public DuplicateTransferRequestException(IdempotentTransferResponse cachedResponse) {
        super("Duplicate transfer request detected");
        this.cachedResponse = cachedResponse;
    }
}
