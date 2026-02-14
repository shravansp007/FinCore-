-- Normalize legacy transaction type values to supported enum constants.
-- Safe to run multiple times.

UPDATE transactions
SET type = UPPER(TRIM(type))
WHERE type IS NOT NULL;

-- Legacy aliases -> canonical values used by the application.
UPDATE transactions
SET type = 'WITHDRAWAL'
WHERE type IN ('WITHDRAW', 'WITHDRAWAL');

UPDATE transactions
SET type = 'PAYMENT'
WHERE type IN ('BILL_PAYMENT', 'PAYMENT');

-- Mark anything unknown explicitly so enum mapping remains safe.
UPDATE transactions
SET type = 'UNKNOWN'
WHERE type IS NULL
   OR type NOT IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'PAYMENT', 'UNKNOWN');
