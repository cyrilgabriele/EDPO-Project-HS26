# 29. Introduce fx-rate-service as a Reference Data bounded context

Date: 2026-05-16

## Status

Accepted

## Context

ADR-0028 commits the platform to converting USDT values into a user's Display Currency at API read time. That conversion needs FX rates. The existing market-data-service ingests push-driven crypto prices from Binance WebSocket; FX rates are slow-moving reference data sourced from a free HTTP provider on a periodic poll. Mixing pull and push semantics inside one service blurs its responsibility, and the existing Market Data Context (per `docs/diagrams/context-map.md`) is defined as "market-price ingestion and publication", which reference rates do not fit.

Options considered: extend market-data-service, host the fetcher inside portfolio-service, or introduce a new service.

## Decision

Introduce a new Spring Boot service **fx-rate-service** in a new **Reference Data** bounded context. The service polls a public FX provider on a 5-minute timer, filters and translates the response into per-pair events, and publishes them to a compacted Kafka topic `reference.fx.rate`, keyed by the currency-pair string (for example `"USDCHF"`). The compacted topic is the cache. Consumers (the scope-03 enrichment app, portfolio-service, transaction-service) materialize it as a GlobalKTable and never call the FX provider directly.

The Reference Data context owns slow-moving reference data for the platform. Today that is FX rates only; future candidates such as trading-pair metadata or exchange calendars would naturally live here.

Detailed topology (control flow, filter/translate steps, retry, schema) is documented in `docs/event-processes/01-fx-rate-ingestion.md`.

## Consequences

market-data-service stays focused on Binance WebSocket ingestion. Reference Data is a clear ownership boundary that other slow-moving providers can join later without expanding the Market Data Context.

One additional deployable. Provider, currency-pair set, and poll cadence are operational settings inside fx-rate-service; changing them does not affect the context boundary or the contract on `reference.fx.rate`.

The free public provider is a single point of failure for the conversion read path. The compacted topic absorbs provider outages: consumers keep serving last-known rates from their GlobalKTable, exactly as ADR-0002 already lets portfolio-service keep serving valuations during market-data-service outages.
