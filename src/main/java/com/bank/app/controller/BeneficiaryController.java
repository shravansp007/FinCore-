package com.bank.app.controller;

import com.bank.app.dto.AddBeneficiaryRequest;
import com.bank.app.dto.BeneficiaryDTO;
import com.bank.app.service.BeneficiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/beneficiaries", "/api/beneficiaries"})
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    @PostMapping
    public ResponseEntity<BeneficiaryDTO> addBeneficiary(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AddBeneficiaryRequest request) {
        return ResponseEntity.ok(beneficiaryService.addBeneficiary(userDetails.getUsername(), request));
    }

    @GetMapping
    public ResponseEntity<List<BeneficiaryDTO>> getMyBeneficiaries(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(beneficiaryService.getMyBeneficiaries(userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BeneficiaryDTO> getBeneficiary(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(beneficiaryService.getBeneficiaryById(id, userDetails.getUsername()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBeneficiary(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        beneficiaryService.deleteBeneficiary(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
