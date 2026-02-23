ALTER TABLE accounts ADD COLUMN IF NOT EXISTS
    account_status VARCHAR(20);

UPDATE accounts
SET account_status = 'ACTIVE'
WHERE account_status IS NULL;

ALTER TABLE accounts
ALTER COLUMN account_status SET DEFAULT 'ACTIVE';

ALTER TABLE accounts
ALTER COLUMN account_status SET NOT NULL;

ALTER TABLE accounts
DROP CONSTRAINT IF EXISTS chk_account_status;

ALTER TABLE accounts ADD CONSTRAINT chk_account_status
CHECK (account_status IN ('ACTIVE', 'DORMANT'));
