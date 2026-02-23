package com.bank.app.controller;

import com.bank.app.dto.AccountDTO;
import com.bank.app.dto.FraudAlertResponse;
import com.bank.app.dto.FraudAlertReviewRequest;
import com.bank.app.dto.FraudRuleConfigRequest;
import com.bank.app.dto.FraudRuleConfigResponse;
import com.bank.app.dto.TransactionResponse;
import com.bank.app.dto.UserSummaryDTO;
import com.bank.app.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Administrative governance and fraud management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "List users", description = "Returns all users for admin review")
    @ApiResponse(responseCode = "200", description = "Users fetched", content = @Content(schema = @Schema(implementation = UserSummaryDTO.class)))
    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryDTO>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @Operation(summary = "List accounts", description = "Returns all accounts")
    @ApiResponse(responseCode = "200", description = "Accounts fetched", content = @Content(schema = @Schema(implementation = AccountDTO.class)))
    @GetMapping("/accounts")
    public ResponseEntity<List<AccountDTO>> getAllAccounts() {
        return ResponseEntity.ok(adminService.getAllAccounts());
    }

    @Operation(summary = "Freeze account", description = "Marks account as inactive")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account frozen"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @PostMapping("/accounts/{id}/freeze")
    public ResponseEntity<AccountDTO> freezeAccount(@Parameter(description = "Account ID", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(adminService.updateAccountStatus(id, false));
    }

    @Operation(summary = "Unfreeze account", description = "Reactivates a frozen account")
    @PostMapping("/accounts/{id}/unfreeze")
    public ResponseEntity<AccountDTO> unfreezeAccount(@Parameter(description = "Account ID", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(adminService.updateAccountStatus(id, true));
    }

    @Operation(summary = "Unlock account lockout", description = "Clears Redis lockout counter for a user account")
    @PostMapping("/accounts/{id}/unlock")
    public ResponseEntity<Map<String, String>> unlockAccount(@Parameter(description = "User ID", required = true) @PathVariable Long id) {
        adminService.unlockAccount(id);
        return ResponseEntity.ok(Map.of("message", "Account unlocked successfully"));
    }

    @Operation(summary = "All transactions", description = "Returns paginated transaction list for admin")
    @GetMapping("/transactions")
    public ResponseEntity<Page<TransactionResponse>> getAllTransactions(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllTransactions(pageable));
    }

    @Operation(summary = "Get fraud rule config", description = "Returns active fraud threshold configuration")
    @GetMapping("/fraud/rules")
    public ResponseEntity<FraudRuleConfigResponse> getFraudRuleConfig() {
        return ResponseEntity.ok(adminService.getFraudRuleConfig());
    }

    @Operation(summary = "Update fraud rule config", description = "Updates configurable fraud thresholds")
    @PutMapping("/fraud/rules")
    public ResponseEntity<FraudRuleConfigResponse> updateFraudRuleConfig(
            @Valid @RequestBody FraudRuleConfigRequest request
    ) {
        return ResponseEntity.ok(adminService.updateFraudRuleConfig(request));
    }

    @Operation(summary = "List fraud alerts", description = "Returns fraud alerts pending or reviewed")
    @GetMapping("/fraud/alerts")
    public ResponseEntity<List<FraudAlertResponse>> getFraudAlerts() {
        return ResponseEntity.ok(adminService.getFraudAlerts());
    }

    @Operation(summary = "Review fraud alert", description = "Marks alert as CLEARED or CONFIRMED")
    @PutMapping("/fraud/alerts/{id}")
    public ResponseEntity<FraudAlertResponse> reviewFraudAlert(
            @Parameter(description = "Fraud alert ID", required = true) @PathVariable Long id,
            @Valid @RequestBody FraudAlertReviewRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(adminService.reviewFraudAlert(id, request, userDetails.getUsername()));
    }
}
