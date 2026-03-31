CREATE TABLE outbox_events (
    id             BIGSERIAL     PRIMARY KEY,
    event_id       VARCHAR(255)  NOT NULL UNIQUE,
    transaction_id VARCHAR(255)  NOT NULL,
    topic          VARCHAR(255)  NOT NULL,
    payload        TEXT          NOT NULL,
    created_at     TIMESTAMP     NOT NULL,
    published_at   TIMESTAMP
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
