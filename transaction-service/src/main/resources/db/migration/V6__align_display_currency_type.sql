-- ──────────────────────────────────────────────────────────────────────────────
-- Transaction Service · widen display_currency to VARCHAR(3) so Hibernate's
-- ddl-auto: validate stops complaining about the bpchar vs varchar mismatch.
-- Same fix as user-service V4. Existing values are preserved unchanged.
-- ──────────────────────────────────────────────────────────────────────────────

ALTER TABLE transaction_record
    ALTER COLUMN display_currency TYPE VARCHAR(3);
