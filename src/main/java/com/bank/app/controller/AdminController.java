package com.bank.app.controller;

import com.bank.app.dto.AccountDTO;
import com.bank.app.dto.TransactionResponse;
import com.bank.app.dto.UserSummaryDTO;
import com.bank.app.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryDTO>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<AccountDTO>> getAllAccounts() {
        return ResponseEntity.ok(adminService.getAllAccounts());
    }

    @PostMapping("/accounts/{id}/freeze")
    public ResponseEntity<AccountDTO> freezeAccount(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.updateAccountStatus(id, false));
    }

    @PostMapping("/accounts/{id}/unfreeze")
    public ResponseEntity<AccountDTO> unfreezeAccount(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.updateAccountStatus(id, true));
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<TransactionResponse>> getAllTransactions(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllTransactions(pageable));
    }
}
