-- Replicated read-model of confirmed users (ADR-0017).
-- Populated by consuming UserConfirmedEvent from the user.confirmed Kafka topic.
-- Enables synchronisation-free user validation at order placement without
-- a synchronous HTTP call to user-service.
CREATE TABLE confirmed_user (
    user_id      VARCHAR(255) NOT NULL PRIMARY KEY,
    user_name    VARCHAR(255),
    confirmed_at TIMESTAMP    NOT NULL
);
