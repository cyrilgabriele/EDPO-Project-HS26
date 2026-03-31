-- ──────────────────────────────────────────────────────────────────────────────
-- CryptoFlow – Per-Service Database Initialisation
--
-- Executed once by PostgreSQL on first container startup (empty volume).
-- Each microservice owns exactly one database; no cross-service table access.
--
-- Re-apply: docker compose down -v && docker compose up -d
-- ──────────────────────────────────────────────────────────────────────────────

CREATE DATABASE user_service_db;
CREATE DATABASE portfolio_service_db;
CREATE DATABASE transaction_service_db;

-- Grant the shared role full access so every service can connect.
GRANT ALL PRIVILEGES ON DATABASE user_service_db        TO cryptoflow;
GRANT ALL PRIVILEGES ON DATABASE portfolio_service_db   TO cryptoflow;
GRANT ALL PRIVILEGES ON DATABASE transaction_service_db TO cryptoflow;
