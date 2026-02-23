-- Plain SQL migration script (run manually if Flyway is not enabled)

CREATE TABLE IF NOT EXISTS user_devices (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_hash VARCHAR(128) NOT NULL,
    first_seen TIMESTAMP NOT NULL DEFAULT NOW(),
    last_seen TIMESTAMP NOT NULL DEFAULT NOW(),
    is_trusted BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_device_hash
    ON user_devices(user_id, device_hash);

CREATE INDEX IF NOT EXISTS idx_user_devices_user_id
    ON user_devices(user_id);

CREATE TABLE IF NOT EXISTS audit_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(64) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    ip_address VARCHAR(100),
    user_agent VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_events_user_id
    ON audit_events(user_id);

CREATE INDEX IF NOT EXISTS idx_audit_events_event_type
    ON audit_events(event_type);
