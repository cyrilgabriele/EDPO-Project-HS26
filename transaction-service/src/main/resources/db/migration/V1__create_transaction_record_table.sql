-- ──────────────────────────────────────────────────────────────────────────────
-- Transaction Service – initial schema
--
-- Provides a durable audit trail for every order placed through the
-- placeOrder BPMN process.  Status transitions: PENDING → APPROVED | REJECTED.
-- ──────────────────────────────────────────────────────────────────────────────

CREATE TABLE transaction_record
(
    id             BIGSERIAL       PRIMARY KEY,
    transaction_id VARCHAR(255)    NOT NULL UNIQUE,
    user_id        VARCHAR(255)    NOT NULL,
    symbol         VARCHAR(20)     NOT NULL,
    amount         NUMERIC(30, 18) NOT NULL,
    target_price   NUMERIC(30, 8)  NOT NULL,
    status         VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    placed_at      TIMESTAMP       NOT NULL,
    resolved_at    TIMESTAMP,
    matched_price  NUMERIC(30, 8)
);
