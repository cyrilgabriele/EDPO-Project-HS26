-- Idempotency guard: records every OrderApprovedEvent that has been successfully applied.
-- The UNIQUE constraint on transaction_id is the authoritative duplicate guard.
CREATE TABLE processed_transaction (
    id             BIGSERIAL    PRIMARY KEY,
    transaction_id VARCHAR(255) NOT NULL UNIQUE,
    processed_at   TIMESTAMP    NOT NULL
);
