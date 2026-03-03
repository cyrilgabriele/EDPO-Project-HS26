-- Portfolio: one row per user
CREATE TABLE portfolio
(
    id      BIGSERIAL    PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE
);

-- Holding: one row per (portfolio, symbol) pair
CREATE TABLE holding
(
    id                     BIGSERIAL       PRIMARY KEY,
    portfolio_id           BIGINT          NOT NULL REFERENCES portfolio (id) ON DELETE CASCADE,
    symbol                 VARCHAR(20)     NOT NULL,
    quantity               NUMERIC(30, 18) NOT NULL,
    average_purchase_price NUMERIC(30, 8)  NOT NULL
);
