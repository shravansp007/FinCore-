package com.bank.app.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("migration")
@RequiredArgsConstructor
public class DatabaseMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("ALTER TABLE accounts ADD COLUMN IF NOT EXISTS account_status VARCHAR(20)");
        jdbcTemplate.execute("UPDATE accounts SET account_status = 'ACTIVE' WHERE account_status IS NULL");
        jdbcTemplate.execute("ALTER TABLE accounts ALTER COLUMN account_status SET DEFAULT 'ACTIVE'");
        jdbcTemplate.execute("ALTER TABLE accounts ALTER COLUMN account_status SET NOT NULL");
        jdbcTemplate.execute("ALTER TABLE accounts DROP CONSTRAINT IF EXISTS chk_account_status");
        jdbcTemplate.execute("ALTER TABLE accounts ADD CONSTRAINT chk_account_status CHECK (account_status IN ('ACTIVE','DORMANT'))");

        System.out.println("Migration completed successfully");

        int exitCode = SpringApplication.exit(applicationContext, () -> 0);
        System.exit(exitCode);
    }
}
