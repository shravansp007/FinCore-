-- Batch jobs support changes

ALTER TABLE accounts
ADD COLUMN IF NOT EXISTS account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

UPDATE accounts
SET account_status = 'ACTIVE'
WHERE account_status IS NULL;
