-- ──────────────────────────────────────────────────────────────────────────────
-- User Service – initial schema
--
-- Replaces Hibernate ddl-auto:update with an explicit Flyway migration so that
-- schema changes are versioned, reviewed, and reproducible (ADR-0007).
-- ──────────────────────────────────────────────────────────────────────────────

-- Table name uses "cryptoflow_user" to avoid the reserved PostgreSQL keyword "user"
CREATE TABLE cryptoflow_user
(
    user_id   VARCHAR(255) NOT NULL PRIMARY KEY,
    user_name VARCHAR(255) NOT NULL,
    password  VARCHAR(255) NOT NULL,
    email     VARCHAR(255) NOT NULL
);

CREATE TABLE user_confirmation_links
(
    user_id        VARCHAR(255) NOT NULL PRIMARY KEY,
    status         VARCHAR(50)  NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    confirmed_at   TIMESTAMP,
    invalidated_at TIMESTAMP
);
