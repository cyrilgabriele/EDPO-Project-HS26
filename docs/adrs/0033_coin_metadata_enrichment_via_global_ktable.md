# 33. Enrich OHLC bars with coin metadata via a stream-table join

Date: 2026-05-17

## Status

Accepted

## Context

The OHLC scope-05 streams app emits closed bars keyed by Binance symbol (BTCUSDT, ETHUSDT, …). Consumers, including the market-data-service dashboard, then need human-friendly information to render: the asset's display name, its logo, its market-cap ranking, the base and quote split. Two questions came up:

1. **Where does the metadata come from?** It is slow-moving, externally sourced, and unrelated to the WebSocket tick feed market-data-service already ingests.
2. **How is each bar enriched without a synchronous lookup on the read path?** Hitting a public API from the dashboard handler would be a regression on ADR-0002 and the read-time-cache pattern used everywhere else in the platform.

A third concern was pattern coverage. The Stream-Table Join pattern (catalog 13) is the foundational join exercised by ADR-0030 for FX in the price-localization topology. The OHLC scope-05 topology so far only demonstrates windowed aggregation and the suppress operator; adding the join here covers the pattern inside market-data-service itself.

## Decision

Introduce a new Spring Boot service **coin-metadata-service** as a second producer in the existing Reference Data bounded context (alongside fx-rate-service, see ADR-0029). It polls CoinGecko's `/coins/markets` endpoint on a long timer (default 24h), maps each configured Binance symbol to its CoinGecko id, and publishes one `CoinMetadata` Avro record per symbol to the compacted Kafka topic `reference.crypto.metadata`, keyed by Binance symbol.

The scope-05 OHLC topology in market-data-service materialises that topic as a **GlobalKTable<String, CoinMetadata>**. After each closed bar is emitted by `suppress(untilWindowCloses)` and the window metadata is filled in, the topology executes a `KStream.leftJoin(GlobalKTable, ...)` against the metadata table using the symbol as the key. The value joiner copies the metadata fields onto the `Ohlc` record before publishing to `crypto.ohlc.{1m,5m,1h}`.

The Avro `Ohlc` schema grows backward-compatibly: `baseAsset`, `quoteAsset`, `name`, `imageUrl`, `marketCapRank`, and `categories` are added as nullable fields with default `null`. Older bars and any future producers that omit them still validate against the registered schema.

## Consequences

The platform now exercises pattern 13 inside market-data-service, in addition to the FX enrichment topology that has it via scope 03. Each OHLC bar arrives at consumers already enriched, so the dashboard no longer needs a separate metadata cache or an HTTP lookup against coin-metadata-service to render a coin's name or logo.

`GlobalKTable` was chosen over a regular `KTable` because the metadata is tiny (one record per supported symbol) and broadcasting the full table to every Streams instance removes the co-partitioning constraint. The same justification applies to `reference.fx.rate` per ADR-0030.

Embedding metadata directly in `Ohlc` rather than emitting a separate `EnrichedOhlc` topic keeps the topic count linear in interval count. The cost is mild schema bloat on every bar; the alternative would have doubled the topic surface and required every existing consumer to choose which stream to read. If the metadata vocabulary grows substantially or starts to evolve faster than OHLC itself, splitting can be reconsidered.

CoinGecko is a free public provider with rate limits. The 24-hour default poll is well within the free tier and matches the rate at which coin-level metadata actually changes. The compacted topic survives provider outages just like `reference.fx.rate` does (ADR-0029): the GlobalKTable continues to serve last-known values. If the provider returns a non-2xx response or no entries, the poller logs the failure and skips the round.

The supported symbols list is configured in `coin-metadata-service/application.yml` as a static mapping from Binance symbol to CoinGecko id. Adding a symbol requires updating that mapping and rolling the service; the OHLC streams app needs no change.
