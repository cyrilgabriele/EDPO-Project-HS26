# Market Scout Implementation Summary

Date: 2026-05-03

## Scope

This summary records the documentation and implementation work completed for `market-order-scout-service`.

## Documentation Changes

- Moved the Market Order Scout context into `docs/cyril-order-scout-service/CONTEXT.md`.
- Updated the domain language so **Market Scout** refers to a flow that ingests bounded futures order-book snapshots and derives ask-side signals.
- Clarified that an **Order Book Snapshot** contains both bid and ask levels, while only ask levels become **Ask Quotes** in the scout topology.
- Added `docs/cyril-order-scout-service/market-scout-topology-plan.md` and updated it to reflect the implemented topology.

## Stream Topology Implemented

The implemented topology is:

```text
crypto.scout.raw -> ask-side content filter -> translator -> threshold filter -> windowed aggregate
```

The service now consumes complete raw partial-depth events from `crypto.scout.raw`, keeps the raw topic replayable, and derives ask-side stream events without mutating the raw event contract.

## Main Code Changes

- Added Kafka Streams support to `market-order-scout-service`.
- Added `MarketScoutTopology` to build the stream-processing pipeline.
- Added `RawOrderBookDepthTimestampExtractor` so transaction time is used as Kafka Streams event time, with fallback to event time and then record timestamp.
- Added `MarketScoutTopologyProperties` for raw topic, derived topics, threshold, window size, and source venue.
- Added `MarketScoutStreamsConfig` to wire Kafka Streams through Spring Boot.
- Added derived Kafka topics:
  - `crypto.scout.ask-quotes`
  - `crypto.scout.ask-opportunities`
  - `crypto.scout.window-summary`
- Added Avro schemas for derived scout events:
  - `AskQuote`
  - `AskOpportunity`
  - `ScoutWindowSummary`
- Added a small registryless `SpecificAvroSerde` for generated Avro `SpecificRecord` types.
- Kept `crypto.scout.raw` as JSON to preserve the existing raw-event contract and stay aligned with ADR-0003.

## Processing Behavior

- The ask-side content filter ignores null, malformed, or empty ask lists.
- The translator flattens each ask level into one `AskQuote`.
- Topic keys remain the trading symbol to preserve per-symbol ordering.
- The threshold filter emits an `AskOpportunity` only when `ask.price <= configured threshold`.
- Opportunities are limited to the configured top-20 visible partial-depth stream.
- The windowed aggregate groups ask opportunities per symbol and event-time window.
- The MVP summary tracks opportunity count and minimum ask price.

## Configuration Added

- `crypto.market-scout.topology.enabled`
- `crypto.market-scout.ask-threshold`
- `crypto.market-scout.window-size`
- `crypto.market-scout.source-venue`
- `crypto.kafka.topic.scout-derived-partitions`
- `crypto.kafka.topic.scout-ask-quotes`
- `crypto.kafka.topic.scout-ask-opportunities`
- `crypto.kafka.topic.scout-window-summary`

The derived scout topic partition count is configurable and currently defaults to `3`, matching the existing platform-level convention for modest parallelism while preserving per-symbol ordering through symbol keys.

## Tests Added

Added Kafka Streams topology tests covering:

- raw events with bids and asks become ask-only quote events
- ask levels flatten into individual translated quote events
- threshold filtering keeps only quotes at or below the configured price
- windowed aggregation groups by symbol and event-time window
- malformed or empty ask lists are ignored without killing stream processing

The Spring application context test disables the topology so it does not require a running Kafka broker.

## Verification

The implementation was verified with:

```sh
mvn clean test -pl market-order-scout-service -am
mvn test -pl market-order-scout-service -am
```

Both commands completed successfully.

## ADR Follow-Up

One implementation decision was significant enough for an ADR: derived Market Scout stream events use Avro contracts while the raw Binance partial-depth event remains JSON. This is recorded in ADR-0022.
