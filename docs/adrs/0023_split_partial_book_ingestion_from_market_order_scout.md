# 23. Split partial book ingestion from Market Order Scout processing

Date: 2026-05-03

## Status

Accepted

## Context

The current Market Scout implementation combines two responsibilities in one service: it subscribes to Binance USD-M Futures Partial Book Depth Streams and it runs the Kafka Streams topology that derives ask-side scout signals from `crypto.scout.raw`.

ADR-0021 selected Partial Book Depth Streams for the MVP because they provide a bounded top-N order-book input that fits the scout topology. ADR-0022 made `crypto.scout.raw` the replay boundary and introduced Avro only for derived scout events. Keeping both responsibilities in one service made the MVP simple, but it weakens the architectural story around Kafka as the service boundary: the service produces a raw stream and then consumes that same stream internally.

The project already has `market-data-service` for ticker price ingestion, so a generic name such as `market-ingestion-service` would be ambiguous.

## Decision

Split the current service into two services:

- `market-partial-book-ingestion-service`
- `market-order-scout-service`

`market-partial-book-ingestion-service` owns the Binance partial book WebSocket connection, exchange payload mapping, reconnect behavior, and raw JSON publication to `crypto.scout.raw`.

`market-order-scout-service` owns the Kafka Streams topology that consumes `crypto.scout.raw`, filters ask-side content, translates ask levels into scout events, applies the ask threshold, and computes windowed summaries.

The `crypto.scout.raw` topic remains the stable contract between the two services. Its payload stays JSON and replayable. Derived scout topics remain Avro:

- `crypto.scout.ask-quotes`
- `crypto.scout.ask-opportunities`
- `crypto.scout.window-summary`

## Consequences

The architecture has clearer responsibility boundaries. The upstream service captures immutable partial order-book data, while the downstream service independently derives business signals from that stream.

Kafka is now demonstrated as an inter-service boundary for the Market Scout flow instead of only as an internal replay point. Other future processors can subscribe to `crypto.scout.raw` without depending on the Market Order Scout service.

The split adds operational overhead: one additional Spring Boot module, service configuration, Docker Compose entry, build artifact, and test surface. This is accepted because the project benefits from a clearer event-driven topology and a stronger separation between exchange-facing ingestion and domain-specific stream processing.

The split does not change the raw event contract, derived event contracts, topic names, or scout processing behavior.
