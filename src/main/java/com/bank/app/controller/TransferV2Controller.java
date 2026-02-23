package com.bank.app.controller;

import com.bank.app.dto.IdempotentTransferResponse;
import com.bank.app.dto.TransactionRequest;
import com.bank.app.dto.TransferV2Request;
import com.bank.app.dto.TransferV2Response;
import com.bank.app.entity.Transaction;
import com.bank.app.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Transfers v2", description = "Versioned transfer APIs and content negotiation")
@SecurityRequirement(name = "bearerAuth")
public class TransferV2Controller {

    public static final String V1_MEDIA_TYPE = "application/vnd.fincore.transfer-v1+json";
    public static final String V2_MEDIA_TYPE = "application/vnd.fincore.transfer-v2+json";

    private final TransferService transferService;

    @Operation(summary = "Transfer funds (v2)", description = "New transfer contract with from/to account fields")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transfer completed", content = @Content(schema = @Schema(implementation = TransferV2Response.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping("/api/v2/transfers")
    public ResponseEntity<TransferV2Response> transferV2(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Client-generated UUID idempotency key", required = true)
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request,
            @Valid @RequestBody TransferV2Request transferRequest
    ) {
        IdempotentTransferResponse result = transferService.transferWithIdempotency(
                userDetails.getUsername(),
                idempotencyKey,
                resolveIpAddress(request),
                toTransactionRequest(transferRequest)
        );

        return ResponseEntity.ok(toV2Response(result, transferRequest));
    }

    @Operation(summary = "Transfer via Accept header (v1)", description = "Content negotiation endpoint using Accept: application/vnd.fincore.transfer-v1+json")
    @PostMapping(value = "/api/transfers", produces = V1_MEDIA_TYPE)
    public ResponseEntity<IdempotentTransferResponse> transferByAcceptV1(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request,
            @Valid @RequestBody TransferV2Request transferRequest
    ) {
        IdempotentTransferResponse result = transferService.transferWithIdempotency(
                userDetails.getUsername(),
                idempotencyKey,
                resolveIpAddress(request),
                toTransactionRequest(transferRequest)
        );
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Transfer via Accept header (v2)", description = "Content negotiation endpoint using Accept: application/vnd.fincore.transfer-v2+json")
    @PostMapping(value = "/api/transfers", produces = V2_MEDIA_TYPE)
    public ResponseEntity<TransferV2Response> transferByAcceptV2(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request,
            @Valid @RequestBody TransferV2Request transferRequest
    ) {
        IdempotentTransferResponse result = transferService.transferWithIdempotency(
                userDetails.getUsername(),
                idempotencyKey,
                resolveIpAddress(request),
                toTransactionRequest(transferRequest)
        );
        return ResponseEntity.ok(toV2Response(result, transferRequest));
    }

    private TransactionRequest toTransactionRequest(TransferV2Request request) {
        return TransactionRequest.builder()
                .type(Transaction.TransactionType.TRANSFER)
                .sourceAccountId(request.getFromAccountId())
                .destinationAccountId(request.getToAccountId())
                .amount(request.getAmount())
                .build();
    }

    private TransferV2Response toV2Response(IdempotentTransferResponse response, TransferV2Request request) {
        return TransferV2Response.builder()
                .transferId(response.getTransactionId() != null ? String.valueOf(response.getTransactionId()) : null)
                .state(response.getStatus())
                .amount(response.getAmount())
                .currency(request.getCurrency() == null || request.getCurrency().isBlank() ? "INR" : request.getCurrency())
                .sourceAccountId(request.getFromAccountId())
                .destinationAccountId(request.getToAccountId())
                .processedAt(response.getTimestamp())
                .build();
    }

    private String resolveIpAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] parts = forwardedFor.split(",");
            if (parts.length > 0 && !parts[0].isBlank()) {
                return parts[0].trim();
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
