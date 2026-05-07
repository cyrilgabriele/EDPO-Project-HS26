# 26. Use Matchable Ask as the cross-service ask contract

Date: 2026-05-07

## Status

Accepted

## Context

Market Order Scout already owns local Avro events for its internal stream-processing view: `AskQuote`, `AskOpportunity`, and `ScoutWindowSummary`. The transaction bounded context now needs ask-side liquidity as matching input, but it should not depend on scout-local events whose fields and thresholds are optimized for market analysis.

The matching key shared by Scout and Transaction is the normalized Binance symbol, for example `ETHUSDT`.

## Decision

Introduce `MatchableAsk` in `shared-events` as the cross-service Avro contract for execution matching. It contains `askQuoteId`, `symbol`, `askPrice`, `askQuantity`, `eventTime`, and `sourceVenue`.

Market Order Scout publishes `MatchableAsk` to `crypto.scout.matchable-asks`, keyed by normalized symbol, after translating raw order-book asks into ask quotes. The `askQuoteId` is deterministic: raw depth event identity plus ask level index. It does not model an `askerId`, because the Binance depth stream exposes price levels rather than individual sellers.

Keep `AskQuote`, `AskOpportunity`, and `ScoutWindowSummary` as scout-local Avro schemas.

## Consequences

Transaction matching can evolve against a narrow, stable ask contract instead of depending on scout analysis events.

`shared-events` now contains Avro generation and a small registryless SpecificRecord serde helper for project-owned Avro topics.

Market Scout emits one additional topic, but it keeps its existing topology outputs unchanged.
