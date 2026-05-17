-- ──────────────────────────────────────────────────────────────────────────────
-- User Service · add Display Currency column (per ADR-0028)
--
-- Display Currency is the ISO-4217 currency the user picked for presentation.
-- It is display-only: storage and order amounts stay USDT. Defaults to 'USD'
-- on creation (since USD is the FX anchor) so consumers never see null.
-- ──────────────────────────────────────────────────────────────────────────────

ALTER TABLE cryptoflow_user
    ADD COLUMN display_currency CHAR(3) NOT NULL DEFAULT 'USD';
