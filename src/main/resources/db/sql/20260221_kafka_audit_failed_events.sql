-- Plain SQL migration for Kafka consumers persistence tables

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    account_id BIGINT NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    performed_by VARCHAR(150) NOT NULL,
    ip_address VARCHAR(100),
    "timestamp" TIMESTAMP NOT NULL DEFAULT NOW(),
    metadata JSONB
);

CREATE INDEX IF NOT EXISTS idx_audit_log_account_id ON audit_log(account_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_timestamp ON audit_log("timestamp");

CREATE TABLE IF NOT EXISTS failed_events (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(200) NOT NULL,
    event_type VARCHAR(100),
    transaction_id BIGINT,
    payload TEXT NOT NULL,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_failed_events_topic ON failed_events(topic);
CREATE INDEX IF NOT EXISTS idx_failed_events_created_at ON failed_events(created_at);
