package com.bank.app.service;

import com.bank.app.dto.AddBeneficiaryRequest;
import com.bank.app.dto.BeneficiaryDTO;
import com.bank.app.entity.Account;
import com.bank.app.entity.Beneficiary;
import com.bank.app.entity.User;
import com.bank.app.exception.BadRequestException;
import com.bank.app.exception.ConflictException;
import com.bank.app.exception.ResourceNotFoundException;
import com.bank.app.exception.UnauthorizedException;
import com.bank.app.repository.BeneficiaryRepository;
import com.bank.app.repository.AccountRepository;
import com.bank.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    @Transactional
    public BeneficiaryDTO addBeneficiary(String userEmail, AddBeneficiaryRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Account beneficiaryAccount = accountRepository.findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (beneficiaryAccount.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Cannot add your own account as beneficiary");
        }

        if (beneficiaryRepository.existsByUserAndAccountId(user, beneficiaryAccount.getId())) {
            throw new ConflictException("This account is already added as beneficiary");
        }

        Beneficiary beneficiary = Beneficiary.builder()
                .nickname(request.getNickname())
                .user(user)
                .account(beneficiaryAccount)
                .transferMode(request.getTransferMode())
                .build();
        beneficiary = beneficiaryRepository.save(beneficiary);
        return mapToDTO(beneficiary);
    }

    public List<BeneficiaryDTO> getMyBeneficiaries(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return beneficiaryRepository.findByUserOrderByNicknameAsc(user).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public void deleteBeneficiary(Long beneficiaryId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        Beneficiary b = beneficiaryRepository.findByIdAndUser(beneficiaryId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary not found"));
        beneficiaryRepository.delete(b);
    }

    public BeneficiaryDTO getBeneficiaryById(Long beneficiaryId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        Beneficiary b = beneficiaryRepository.findByIdAndUser(beneficiaryId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary not found"));
        return mapToDTO(b);
    }

    public Long resolveBeneficiaryToAccountId(Long beneficiaryId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        Beneficiary b = beneficiaryRepository.findByIdAndUser(beneficiaryId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary not found"));
        return b.getAccount().getId();
    }

    public boolean isBeneficiaryAccount(String userEmail, Long accountId) {
        if (accountId == null) {
            return false;
        }
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return beneficiaryRepository.existsByUserAndAccountId(user, accountId);
    }

    private BeneficiaryDTO mapToDTO(Beneficiary b) {
        Account acc = b.getAccount();
        String holderName = acc.getUser().getFirstName() + " " + acc.getUser().getLastName();
        return BeneficiaryDTO.builder()
                .id(b.getId())
                .nickname(b.getNickname())
                .accountNumber(acc.getAccountNumber())
                .accountHolderName(holderName)
                .transferMode(b.getTransferMode())
                .build();
    }
}
