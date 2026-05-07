# 22. Use Avro contracts for derived Market Scout events

Date: 2026-05-03

## Status

Accepted

## Context

ADR-0003 established JSON serialization for Kafka messages to keep the platform simple and inspectable. The Market Scout MVP still needs a stream-processing topology with explicit event contracts for translated and aggregated events. The raw Binance partial-depth event is an exchange-facing capture format and must remain easy to replay without losing bid levels, ask levels, timestamps, or update identifiers.

## Decision

Keep `crypto.scout.raw` as JSON and introduce Avro schemas only for derived Market Scout stream events:

- `AskQuote`
- `AskOpportunity`
- `ScoutWindowSummary`

The raw JSON topic remains the replay boundary. The Avro boundary starts after the ask-side content filter, where the service owns the translated scout events and windowed output contracts.

## Consequences

This preserves the simplicity and replayability of the raw ingestion topic while giving the derived topology stronger, explicit contracts. It also creates a deliberate exception to the platform-wide JSON default from ADR-0003 for stream-processing outputs owned by `market-order-scout-service`.

The current implementation uses local generated Avro `SpecificRecord` classes and a registryless serde, so it does not add Schema Registry infrastructure. If derived scout topics become cross-service contracts with independent producers or consumers, the project should revisit Schema Registry and compatibility rules.
