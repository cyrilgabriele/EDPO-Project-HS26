-- Decoded Kafka audit projection for the matching pipeline.
-- Populated independently from the Kafka Streams matching topology so the
-- dashboard can show what entered and left the matcher without influencing it.

CREATE TABLE matching_audit_buy_bid
(
    transaction_id VARCHAR(255)    PRIMARY KEY,
    user_id        VARCHAR(255)    NOT NULL,
    symbol         VARCHAR(20)     NOT NULL,
    bid_price      NUMERIC(30, 18) NOT NULL,
    bid_quantity   NUMERIC(30, 18) NOT NULL,
    created_at     TIMESTAMP       NOT NULL,
    topic          VARCHAR(255)    NOT NULL,
    partition_id   INTEGER         NOT NULL,
    offset_id      BIGINT          NOT NULL,
    consumed_at    TIMESTAMP       NOT NULL
);

CREATE INDEX idx_matching_audit_buy_bid_consumed_at
    ON matching_audit_buy_bid (consumed_at DESC);

CREATE TABLE matching_audit_matchable_ask
(
    ask_quote_id VARCHAR(255)    PRIMARY KEY,
    symbol       VARCHAR(20)     NOT NULL,
    ask_price    NUMERIC(30, 18) NOT NULL,
    ask_quantity NUMERIC(30, 18) NOT NULL,
    event_time   TIMESTAMP       NOT NULL,
    source_venue VARCHAR(255)    NOT NULL,
    topic        VARCHAR(255)    NOT NULL,
    partition_id INTEGER         NOT NULL,
    offset_id    BIGINT          NOT NULL,
    consumed_at  TIMESTAMP       NOT NULL
);

CREATE INDEX idx_matching_audit_matchable_ask_consumed_at
    ON matching_audit_matchable_ask (consumed_at DESC);

CREATE TABLE matching_audit_order_match
(
    transaction_id   VARCHAR(255)    PRIMARY KEY,
    ask_quote_id     VARCHAR(255)    NOT NULL UNIQUE,
    symbol           VARCHAR(20)     NOT NULL,
    matched_price    NUMERIC(30, 18) NOT NULL,
    matched_quantity NUMERIC(30, 18) NOT NULL,
    bid_created_at   TIMESTAMP       NOT NULL,
    ask_event_time   TIMESTAMP       NOT NULL,
    source_venue     VARCHAR(255)    NOT NULL,
    topic            VARCHAR(255)    NOT NULL,
    partition_id     INTEGER         NOT NULL,
    offset_id        BIGINT          NOT NULL,
    consumed_at      TIMESTAMP       NOT NULL
);

CREATE INDEX idx_matching_audit_order_match_consumed_at
    ON matching_audit_order_match (consumed_at DESC);
