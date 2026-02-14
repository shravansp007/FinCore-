package com.bank.app.controller;

import com.bank.app.dto.AccountOperationRequest;
import com.bank.app.dto.BillPaymentRequest;
import com.bank.app.dto.TransactionRequest;
import com.bank.app.dto.TransactionResponse;
import com.bank.app.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionService.createTransaction(userDetails.getUsername(), request));
    }

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AccountOperationRequest request) {
        return ResponseEntity.ok(transactionService.deposit(userDetails.getUsername(), request));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AccountOperationRequest request) {
        return ResponseEntity.ok(transactionService.withdraw(userDetails.getUsername(), request));
    }

    @PostMapping("/bill-payment")
    public ResponseEntity<TransactionResponse> payBill(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BillPaymentRequest request) {
        return ResponseEntity.ok(transactionService.payBill(userDetails.getUsername(), request));
    }

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getUserTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String direction,
            @PageableDefault(size = 10, sort = "transactionDate") Pageable pageable) {
        return ResponseEntity.ok(transactionService.getUserTransactions(userDetails.getUsername(), startDate, endDate, direction, pageable));
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<Page<TransactionResponse>> getAccountTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long accountId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(transactionService.getAccountTransactions(accountId, userDetails.getUsername(), pageable));
    }

    @GetMapping("/account/{accountId}/mini-statement")
    public ResponseEntity<List<TransactionResponse>> getMiniStatement(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(transactionService.getMiniStatement(accountId, userDetails.getUsername(), limit));
    }

    @GetMapping("/reference/{reference}")
    public ResponseEntity<TransactionResponse> getTransactionByReference(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String reference) {
        return ResponseEntity.ok(transactionService.getTransactionByReference(reference, userDetails.getUsername()));
    }

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

    @GetMapping("/statement")
    public ResponseEntity<byte[]> downloadStatementLegacy(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate endDate) {
        return downloadStatement(userDetails, startDate, endDate);
    }
}
