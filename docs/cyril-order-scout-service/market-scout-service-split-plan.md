# Market Scout Service Split Plan

Date: 2026-05-03

## Summary

Split the previous combined Market Scout service into two services:

- `market-partial-book-ingestion-service`
- `market-order-scout-service`

The upstream service owns the Binance USD-M Futures Partial Book Depth WebSocket subscription and publishes complete raw order-book depth events to `crypto.scout.raw`.

The downstream service owns the Market Order Scout Kafka Streams topology and consumes `crypto.scout.raw` to derive ask quotes, ask opportunities, and window summaries.

## Target Architecture

```text
Binance USD-M Futures Partial Book Depth Stream
  -> market-partial-book-ingestion-service
  -> crypto.scout.raw
  -> market-order-scout-service
  -> crypto.scout.ask-quotes
  -> crypto.scout.ask-opportunities
  -> crypto.scout.window-summary
```

## Service Responsibilities

### market-partial-book-ingestion-service

- Connect to Binance USD-M Futures Partial Book Depth Streams.
- Keep the existing configurable symbols, depth, update speed, and reconnect behavior.
- Map exchange payloads into `RawOrderBookDepthEvent`.
- Publish complete raw events to `crypto.scout.raw` as JSON.
- Own only upstream capture concerns; it must not filter asks, apply scout thresholds, or aggregate windows.

### market-order-scout-service

- Consume `RawOrderBookDepthEvent` from `crypto.scout.raw`.
- Preserve the implemented topology:

```text
crypto.scout.raw -> ask-side content filter -> translator -> threshold filter -> windowed aggregate
```

- Produce derived Avro events to:
  - `crypto.scout.ask-quotes`
  - `crypto.scout.ask-opportunities`
  - `crypto.scout.window-summary`
- Own scout-specific configuration such as ask threshold, summary window size, source venue, and derived topic names.
- Use transaction time as Kafka Streams event time, with the existing fallback behavior.

## Implementation Steps

1. Create a new Maven module named `market-partial-book-ingestion-service`.
2. Move the Binance partial-depth WebSocket adapter and raw Kafka producer from the current service into the new ingestion service.
3. Rename the combined service module to `market-order-scout-service`.
4. Remove Binance WebSocket and raw producer wiring from `market-order-scout-service`; keep only the Kafka Streams topology and derived-topic configuration.
5. Keep `RawOrderBookDepthEvent` and `OrderBookLevel` in `shared-events` so both services use the same raw topic contract.
6. Update the root Maven module list, Dockerfiles, Docker Compose services, README service table, and any references to the old module name.
7. Keep `crypto.scout.raw` unchanged as the compatibility boundary between the two services.

## Test Plan

- Run the ingestion service tests to verify Binance payload mapping and raw event publishing.
- Run the scout topology tests to verify ask filtering, translation, threshold filtering, windowed aggregation, and malformed input handling.
- Run the Spring context tests for both services with external dependencies disabled where needed.
- Run the Maven module test set:

```sh
mvn test -pl market-partial-book-ingestion-service,market-order-scout-service -am
```

- In Docker Compose, verify that `market-partial-book-ingestion-service` produces `crypto.scout.raw` and `market-order-scout-service` independently produces the three derived scout topics.

## Assumptions

- The raw topic name remains `crypto.scout.raw`.
- The raw topic payload remains JSON, aligned with ADR-0003 and ADR-0022.
- Derived Market Scout events remain Avro without Schema Registry for the current project scope.
- The split is a service-boundary change only; the Market Scout topology behavior should not change.
