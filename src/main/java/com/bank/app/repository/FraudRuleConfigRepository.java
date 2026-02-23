package com.bank.app.repository;

import com.bank.app.entity.FraudRuleConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FraudRuleConfigRepository extends JpaRepository<FraudRuleConfig, Long> {
    Optional<FraudRuleConfig> findByRuleName(String ruleName);
}
