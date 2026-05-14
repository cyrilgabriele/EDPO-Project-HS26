# 27. Use a 30-second event-time window for bid/ask matching

Date: 2026-05-07

## Status

Accepted

## Context

The previous place-order implementation approved orders from ticker-price events held in an in-memory map. That confused ticker valuation prices with executable ask liquidity and lost pending order state on service restart.

The matching decision needs event-time semantics: a user buy bid is valid from `BuyBid.createdAt`, while an exchange ask is valid at `MatchableAsk.eventTime`.

## Decision

Transaction Service owns a Kafka Streams topology that consumes `transaction.buy-bids` and `crypto.scout.matchable-asks`, both keyed by normalized Binance symbol. A bid can match an ask only when:

- `ask.eventTime >= bid.createdAt`
- `ask.eventTime <= bid.createdAt + 30s`
- `bidPrice >= askPrice`
- `bidQuantity <= askQuantity`

Use a 30-second logical validity window plus a 5-second grace/fallback margin. Camunda keeps the event-based gateway shape and changes the rejection timer to `PT35S`; no unmatched Kafka event is emitted.

Each Matchable Ask is allocated to at most one still-pending Buy Bid using price-time priority: highest bid price, then earliest bid creation time, then lexicographic transaction id for deterministic replay and tests. Once a transaction wins, duplicate match decisions for that transaction are ignored.

## Consequences

Matching no longer depends on local heap state or ticker prices.

The 30-second validity window is event-time based. The extra 5 seconds reflects the lecture windowing idea of allowing a bounded late/fallback margin before the workflow follows the timeout path.

Losing bids remain pending and can match later asks within their own validity window. If no `priceMatchedEvent` reaches Camunda before `PT35S`, the existing rejection path marks the transaction rejected and sends the rejection email.
