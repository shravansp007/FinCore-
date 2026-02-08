package com.bank.app.controller;

import com.bank.app.dto.AccountDTO;
import com.bank.app.dto.CreateAccountRequest;
import com.bank.app.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountDTO> createAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.ok(accountService.createAccount(userDetails.getUsername(), request));
    }

    @GetMapping
    public ResponseEntity<List<AccountDTO>> getUserAccounts(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(accountService.getUserAccounts(userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountDTO> getAccountById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(accountService.getAccountById(id, userDetails.getUsername()));
    }

    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<AccountDTO> getAccountByNumber(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getAccountByNumber(accountNumber, userDetails.getUsername()));
    }
}
