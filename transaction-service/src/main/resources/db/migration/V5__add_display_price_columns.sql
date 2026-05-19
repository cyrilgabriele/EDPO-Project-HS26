-- ──────────────────────────────────────────────────────────────────────────────
-- Transaction Service · capture the user-facing price alongside the USDT bid.
--
-- target_price stays as the USDT value the matching topology compares against.
-- Two new columns record what the user actually typed and in which Display
-- Currency, so the order audit reads back the same numbers the user submitted.
-- Existing rows are backfilled assuming the original entry was already in USD
-- (true until this migration ships).
-- ──────────────────────────────────────────────────────────────────────────────

ALTER TABLE transaction_record
    ADD COLUMN target_price_display NUMERIC(30, 8);

ALTER TABLE transaction_record
    ADD COLUMN display_currency CHAR(3);

UPDATE transaction_record
SET target_price_display = target_price,
    display_currency = 'USD'
WHERE target_price_display IS NULL;

ALTER TABLE transaction_record
    ALTER COLUMN target_price_display SET NOT NULL;

ALTER TABLE transaction_record
    ALTER COLUMN display_currency SET NOT NULL;
