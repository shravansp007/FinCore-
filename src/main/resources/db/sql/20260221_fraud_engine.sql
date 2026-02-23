-- Rule-based fraud engine tables

CREATE TABLE IF NOT EXISTS fraud_rule_config (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL UNIQUE,
    threshold_value NUMERIC(19, 2) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO fraud_rule_config (rule_name, threshold_value, updated_at)
SELECT 'AMOUNT_THRESHOLD', 50000.00, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM fraud_rule_config WHERE rule_name = 'AMOUNT_THRESHOLD'
);

CREATE TABLE IF NOT EXISTS fraud_alerts (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    risk_score INT NOT NULL,
    triggered_rules VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    reviewed_by VARCHAR(150),
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fraud_alerts_status ON fraud_alerts(status);
CREATE INDEX IF NOT EXISTS idx_fraud_alerts_account_id ON fraud_alerts(account_id);
CREATE INDEX IF NOT EXISTS idx_fraud_alerts_transaction_id ON fraud_alerts(transaction_id);

CREATE TABLE IF NOT EXISTS transfer_history (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL UNIQUE,
    source_account_id BIGINT NOT NULL,
    destination_account_id BIGINT NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_transfer_history_source_created
    ON transfer_history(source_account_id, created_at);
CREATE INDEX IF NOT EXISTS idx_transfer_history_source_destination
    ON transfer_history(source_account_id, destination_account_id);
