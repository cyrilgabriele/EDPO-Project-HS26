-- ──────────────────────────────────────────────────────────────────────────────
-- User Service · align display_currency column type with Hibernate's expectation.
--
-- V3 created the column as CHAR(3) which Postgres exposes as bpchar.
-- Hibernate's @Column(length = 3, nullable = false) maps to VARCHAR(3), so
-- ddl-auto: validate fails the schema check. ALTER widens to VARCHAR(3)
-- without data loss; existing 'USD' values are preserved.
-- ──────────────────────────────────────────────────────────────────────────────

ALTER TABLE cryptoflow_user
    ALTER COLUMN display_currency TYPE VARCHAR(3);
