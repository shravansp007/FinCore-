package com.bank.app.controller;

import com.bank.app.dto.AccountOperationRequest;
import com.bank.app.dto.BillPaymentRequest;
import com.bank.app.dto.IdempotentTransferResponse;
import com.bank.app.dto.TransactionRequest;
import com.bank.app.dto.TransactionResponse;
import com.bank.app.service.TransferService;
import com.bank.app.service.TransactionService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction and transfer operations")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransferService transferService;

    @Operation(summary = "Create transaction", description = "Creates DEPOSIT, WITHDRAW, or TRANSFER transaction")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction created", content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid transaction request")
    })
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionService.createTransaction(userDetails.getUsername(), request));
    }

    @Operation(summary = "Transfer funds (v1)", description = "Performs transfer using X-Idempotency-Key and returns idempotent response")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transfer completed", content = @Content(schema = @Schema(implementation = IdempotentTransferResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or missing idempotency key")
    })
    @PostMapping("/transfer")
    public ResponseEntity<IdempotentTransferResponse> transfer(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Client-generated UUID idempotency key", required = true)
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpServletRequest,
            @Valid @RequestBody TransactionRequest request
    ) {
        String ipAddress = resolveIpAddress(httpServletRequest);
        return ResponseEntity.ok(
                transferService.transferWithIdempotency(userDetails.getUsername(), idempotencyKey, ipAddress, request)
        );
    }

    @Operation(summary = "Deposit", description = "Deposits amount into an account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Deposit completed", content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AccountOperationRequest request) {
        return ResponseEntity.ok(transactionService.deposit(userDetails.getUsername(), request));
    }

    @Operation(summary = "Withdraw", description = "Withdraws amount from an account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Withdrawal completed", content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Insufficient balance or invalid request")
    })
    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AccountOperationRequest request) {
        return ResponseEntity.ok(transactionService.withdraw(userDetails.getUsername(), request));
    }

    @Operation(summary = "Bill payment", description = "Pays a bill from account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bill payment completed", content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping("/bill-payment")
    public ResponseEntity<TransactionResponse> payBill(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BillPaymentRequest request) {
        return ResponseEntity.ok(transactionService.payBill(userDetails.getUsername(), request));
    }

    @Operation(summary = "List user transactions", description = "Returns paginated transactions with optional filters")
    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getUserTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Start date filter")
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date filter")
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate endDate,
            @Parameter(description = "Direction filter: CREDIT/DEBIT/ALL")
            @RequestParam(required = false) String direction,
            @PageableDefault(size = 10, sort = "transactionDate") Pageable pageable) {
        return ResponseEntity.ok(transactionService.getUserTransactions(userDetails.getUsername(), startDate, endDate, direction, pageable));
    }

    @Operation(summary = "Account transactions", description = "Returns paginated transactions for one account")
    @GetMapping("/account/{accountId}")
    public ResponseEntity<Page<TransactionResponse>> getAccountTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Account ID", required = true)
            @PathVariable Long accountId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(transactionService.getAccountTransactions(accountId, userDetails.getUsername(), pageable));
    }

    @Operation(summary = "Mini statement", description = "Returns latest N transactions for account")
    @GetMapping("/account/{accountId}/mini-statement")
    public ResponseEntity<List<TransactionResponse>> getMiniStatement(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Account ID", required = true)
            @PathVariable Long accountId,
            @Parameter(description = "Maximum rows")
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(transactionService.getMiniStatement(accountId, userDetails.getUsername(), limit));
    }

    @Operation(summary = "Find by reference", description = "Returns transaction by reference")
    @GetMapping("/reference/{reference}")
    public ResponseEntity<TransactionResponse> getTransactionByReference(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Transaction reference", required = true)
            @PathVariable String reference) {
        return ResponseEntity.ok(transactionService.getTransactionByReference(reference, userDetails.getUsername()));
    }

    @Operation(summary = "Download statement PDF", description = "Generates account statement PDF")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "PDF generated", content = @Content(mediaType = "application/pdf"))
    })
    @GetMapping("/statement/download")
    public ResponseEntity<byte[]> downloadStatement(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate endDate) {
        byte[] pdfBytes = transactionService.generateStatementPdf(userDetails.getUsername(), startDate, endDate);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename("bank-statement.pdf")
                        .build()
        );
        headers.setContentLength(pdfBytes.length);

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @Operation(summary = "Legacy statement route", description = "Backward-compatible statement endpoint")
    @GetMapping("/statement")
    public ResponseEntity<byte[]> downloadStatementLegacy(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate endDate) {
        return downloadStatement(userDetails, startDate, endDate);
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
