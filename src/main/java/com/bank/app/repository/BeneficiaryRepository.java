package com.bank.app.repository;

import com.bank.app.entity.Beneficiary;
import com.bank.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {
    List<Beneficiary> findByUserOrderByNicknameAsc(User user);
    Optional<Beneficiary> findByIdAndUser(Long id, User user);
    boolean existsByUserAndAccountId(User user, Long accountId);
}
