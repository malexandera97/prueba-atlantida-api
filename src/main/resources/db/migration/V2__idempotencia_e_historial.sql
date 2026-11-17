ALTER TABLE transfers ADD COLUMN idempotency_key VARCHAR(100);
ALTER TABLE transfers ADD COLUMN source_balance_after NUMERIC(18,2);
ALTER TABLE transfers ADD COLUMN destination_balance_after NUMERIC(18,2);

ALTER TABLE transfers ADD CONSTRAINT uk_transfers_idempotency_key UNIQUE (idempotency_key);

CREATE INDEX ix_transfers_source_created ON transfers (source_account_id, created_at DESC);
CREATE INDEX ix_transfers_destination_created ON transfers (destination_account_id, created_at DESC);
