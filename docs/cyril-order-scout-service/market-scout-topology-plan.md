# Market Scout Topology Plan

This document captures the Market Order Scout stream-processing topology implemented in `market-order-scout-service`.

## Current State

`market-partial-book-ingestion-service` subscribes to the Binance USD-M Futures Partial Book Depth Stream with depth `20` and publishes complete raw events to `crypto.scout.raw`. `market-order-scout-service` consumes that raw topic and owns the downstream stream-processing topology.

`RawOrderBookDepthEvent` should remain replayable as the raw topic contract. Keep bids, asks, symbol, event time, transaction time, and update IDs in the raw event so future processors can replay or reinterpret the exchange payload without returning to Binance.

Full local order-book reconstruction is intentionally deferred. ADR-0021 records that future Diff Book Depth work should combine an initial snapshot with sequence-validated depth updates before deriving scout opportunities from a complete local book.

## Implemented Topology

```text
crypto.scout.raw
  -> ask-side content filter
  -> translator
  -> crypto.scout.ask-quotes
  -> crypto.scout.matchable-asks
  -> threshold filter
  -> crypto.scout.ask-opportunities
  -> windowed aggregate
  -> crypto.scout.window-summary
```

The implemented derived topics are:

- `crypto.scout.ask-quotes`
- `crypto.scout.matchable-asks`
- `crypto.scout.ask-opportunities`
- `crypto.scout.window-summary`

## Implementation Slices

### 1. Ask-side Content Filter

- Consume `RawOrderBookDepthEvent` from `crypto.scout.raw`.
- Drop bids and update metadata not needed by downstream ask processing.
- Keep symbol, transaction time, event time, and ask levels.

### 2. Translator

- Flatten ask levels into one event per ask level.
- Produce an `AskQuote`-style event containing symbol, price, quantity, transaction time, event time, source venue, and received/processed timestamp.
- Keep the topic key as symbol for per-symbol ordering.

### 3. Threshold Filter

- Add a configurable threshold for the MVP, defaulting to one global value.
- Emit **Ask Opportunity** events only when `ask.price <= threshold`.
- Document that opportunities are only within the configured top-20 visible depth.

### 4. Matchable Ask Contract

- Translate every `AskQuote` into a `MatchableAsk` for `transaction-service`.
- Publish the stream to `crypto.scout.matchable-asks`.
- Keep this stream independent from the threshold filter so bid matching uses
  the current ask book levels, not only scout opportunities.
- Use symbol keys so `transaction-service` can align bids and asks by trading
  symbol in its own Kafka Streams topology.

### 5. Avro Boundary

- Introduce Avro schemas for derived scout events, not for the existing raw JSON event.
- Keep `crypto.scout.raw` JSON initially to stay aligned with ADR-0003.
- Use Avro for the translated and output topics to satisfy the stream-processing requirement.

### 6. Windowed Operation

- Add a time-windowed aggregate over ask opportunities.
- Use transaction time as event time.
- MVP aggregate: per symbol, per window, count opportunities and track min ask price.
- Output a windowed scout summary topic.

## Test Plan

For the implemented topology, run:

```sh
mvn test -pl market-order-scout-service -am
```

The topology test suite covers:

- raw event with bids and asks becomes ask-only content event
- ask levels flatten into individual translated quote events
- threshold keeps only quotes at or below configured price
- every ask quote is also emitted as a matchable ask for transaction matching
- windowed aggregate groups by symbol and event-time window
- malformed or empty ask lists are ignored without killing stream processing
