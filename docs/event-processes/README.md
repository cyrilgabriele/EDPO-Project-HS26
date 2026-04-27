# Event Processing Scopes

Working specs for the stream-processing extensions to CryptoFlow, one file per scope. Each file holds the information needed to derive a topology and later write an ADR.

## Committed scopes

| # | Scope | Type | Owner | File |
|---|---|---|---|---|
| 1 | FX rate ingestion | stateless | TBD | [01-fx-rate-ingestion.md](01-fx-rate-ingestion.md) |
| 2 | Price stream sanity | stateless | TBD | [02-price-stream-sanity.md](02-price-stream-sanity.md) |
| 3 | FX price enrichment | stateful | TBD | [03-fx-price-enrichment.md](03-fx-price-enrichment.md) |
| 4 | Portfolio valuation | stateful | TBD | [04-portfolio-valuation.md](04-portfolio-valuation.md) |
| 5 | OHLC candlesticks | windowed | TBD | [05-ohlc-candles.md](05-ohlc-candles.md) |
| 6 | Technical indicators | windowed | TBD | [06-technical-indicators.md](06-technical-indicators.md) |

## Proposals pending discussion

- [market-data-topology-proposal.md](market-data-topology-proposal.md) — coherent topology proposal using existing market data to demonstrate stateless, stateful, and windowed processing.
- [order-book-options.md](order-book-options.md) — order-book extension; closes the streaming-join gap.

## Coverage vs the required pattern list

| Required pattern | Scope(s) |
|---|---|
| Single-Event Processing | 1, 2 |
| Processing with Local State | 4, 5, 6 |
| Multiphase Processing / Repartitioning | 4 (table-join → groupBy user), 6 if fed from 5 |
| Stream-Table Join (external lookup) | 3, 4 |
| Table-Table Join | 4 (prices KTable × holdings KTable) |
| Streaming Join | — **gap**, closed by order-book option 3 |
| Out-of-Sequence Events | 5, 6 (grace period) |
| Reprocessing | cross-cutting — commit to demonstrating on 5 or 6 |
| Interactive Queries | 4 (portfolio); optionally 5 (latest candle) |

## Cross-cutting concerns

- **Avro schemas** live in `shared-events` (patterns 20, 21).
- **Processing guarantees** — each app declares at-least-once or exactly-once in its scope file (pattern 08).
- **Reprocessing strategy** (pattern 17) — declare on at least one scope; offset reset vs parallel-version.
- **Idempotent consumer** — portfolio-service already uses `ProcessedTransactionRepository` (pattern 30); reuse that story.

## Ownership split (proposal — decide and fill in)

- **Option A:** A owns FX end-to-end (1 + 3) + OHLC (5); B owns price sanity (2) + portfolio (4) + indicators (6). Clean story per person.
- **Option B:** A owns 1, 4, 5; B owns 2, 3, 6. Requires coordination on the FX contract across the 1 → 3 boundary.

## Conventions from TA reference implementations

Drawn from `lab11-yellingapp`, `lab12Part1-cryptosentiment`, `lab12Part2-eyeTracking1` (cloned under `../references` by convention).

### Kafka Streams DSL

- **Stages as separate operators** — one `filter` / `mapValues` per logical step, not chained into one expression. Easier to reason about and renders cleanly as a topology diagram.
- **Debug taps**: `Printed.<K, V>toSysOut().withLabel("stage-name")` between stages for quick inspection and labelled diagram export.
- **Explicit serdes at every edge**: `Consumed.with(keySerde, valueSerde)` on every `stream(...)`, `Produced.with(keySerde, valueSerde)` on every `to(...)`.
- **Routing**: modern `.split(Named.as("prefix-")).branch(...)` from `lab12Part1` — not the deprecated array-returning `.branch()` seen in `lab12Part2`.

### Avro + serdes

- Schemas under `src/main/avro/*.avsc`; Avro namespace matches the generated Java package (we use `ch.unisg.cryptoflow.shared.events.*`).
- Centralize Kafka Streams serde construction in an `AvroSerdes` factory class (per `lab12Part1`), one `public static Serde<T> T(String url, boolean isKey)` method per record, wrapping `SpecificAvroSerde<T>` configured with `schema.registry.url`.
- Schema-Registry URL in labs: `http://localhost:8081` — the service is **not** currently in our `docker-compose` and will need to be added.

## Open cross-cutting decision — serialization

`shared-events` today uses **Java records + Jackson/JSON**, not Avro. The course requires Avro. Options:

- **A.** Migrate existing records (`CryptoPriceUpdatedEvent`, `OrderApprovedEvent`, …) to Avro-generated classes. Single format everywhere; largest change.
- **B.** Keep existing events in JSON, emit Avro only from new scopes (`reference.fx.rate`, `crypto.price.clean`, `crypto.price.localized`, `portfolio.value.updated`, `crypto.ohlc.*`, `crypto.indicator.*`). Scope 2 bridges JSON-in / Avro-out.
- **C.** Dual serdes during a staged migration, drop JSON once everything is Avro.

**Proposed for next-week milestone:** option **B** — unblocks scopes 1 and 2 without touching existing producers; revisit later.

## A scope is ready for implementation when

1. Inputs and outputs have finalized Avro schemas committed in `shared-events`.
2. Windowing parameters (if any) are chosen.
3. Open-decisions list is empty.
4. Owner is assigned.

## Related

- Pattern catalog: [../agent-instructions/events/README.md](../agent-instructions/events/README.md)
- Existing ADRs: [../adrs/](../adrs/)
