# 04. Real-Time Portfolio Valuation

**Type:** stateful &nbsp;|&nbsp; **Required patterns:** Stream-Table Join, Table-Table Join, Processing with Local State, Multiphase Repartitioning, Interactive Queries &nbsp;|&nbsp; **Owner:** Janni &nbsp;|&nbsp; **Status:** implemented (ADR-0034)

## Purpose

Continuously compute each user's total portfolio value (sum of `qty × current price` across all holdings) and expose it via Kafka Streams interactive queries so the portfolio dashboard can read it without hitting PostgreSQL.

## Decisions locked in (cross-context)

- **Aggregation stays USDT-denominated.** Holdings, position values, and `PortfolioValue` totals are computed in USDT. Display Currency conversion happens at API read time on the consuming endpoint, not inside this streams app. See [ADR-0028](../adrs/0028_display_currency_as_user_identity_data.md) and [ADR-0031](../adrs/0031_venue_native_ohlc_with_read_time_conversion.md) for the same doctrine applied to OHLC.
- **Read-time inputs.** The HTTP read path additionally consumes `crypto.price.localized` ([ADR-0030](../adrs/0030_stream_table_join_for_price_localization.md)) and `user.display-currency` (compacted, see [ADR-0028](../adrs/0028_display_currency_as_user_identity_data.md)) so it can convert at the endpoint.
- **Serialization:** `PortfolioValue` is Avro per [ADR-0032](../adrs/0032_avro_schema_registry_for_derived_events.md).
- **Cross-context contract:** documented in [00-display-currency-cross-context.md](00-display-currency-cross-context.md).

## Why this matters

Single most pattern-dense app in the set — covers table-table join, per-key aggregation with repartitioning, and interactive queries all in one topology. Directly user-visible ("what am I worth right now?"). Enables a report comparison of state-store vs Postgres reads.

## Patterns hit

| File | Role |
|---|---|
| `13-stream-table-join.md` | optional stream-table join variant for per-tick emission |
| `14-table-table-join.md` | prices KTable × holdings KTable (foreign-key on symbol) |
| `11-processing-with-local-state.md` | per-user aggregation via `groupByKey` on repartitioned stream |
| `12-multiphase-repartitioning.md` | holdings keyed by `(user, symbol)` re-keyed to `user` for the sum |
| `18-interactive-queries.md` | REST endpoint → local state store |
| `06-state-and-stream-table-duality.md` | orders stream → holdings KTable; prices stream → prices KTable |

## Topology

```
transaction.order.approved
       │
       ▼ (fold signed qty into holdings table)
 holdings KTable                         crypto.price.raw
 key: (userId, symbol)                         │
 value: qty                                    ▼ (toTable, keep latest per symbol)
       │                                 prices KTable
       │                                 key: symbol
       └──────────── FK join on symbol ──────┘
                           │
                           ▼ (qty × priceUsdt)
              position-value KTable
              key: (userId, symbol)
                           │
                           ▼ groupBy(userId) + reduce(sum, keep breakdown)
              portfolio-value KTable
              key: userId
                           │
          ┌────────────────┴───────────────┐
          ▼                                ▼
 portfolio.value.updated         Interactive queries
 (key = userId)                  GET /portfolios/{userId}/streams-value
```

## Inputs

| Topic | Type | Key | Value |
|---|---|---|---|
| `transaction.order.approved` | KStream | userId (or orderId) | existing `OrderApprovedEvent` |
| `crypto.price.raw`           | KStream | symbol | existing `CryptoPriceUpdatedEvent` |

## Outputs

| Topic | Cleanup | Key | Value |
|---|---|---|---|
| `portfolio.value.updated` | compact | userId | `PortfolioValue` (Avro) |

### Avro — `ch.unisg.cryptoflow.events.avro.PortfolioValue`

```
userId:     string
totalUsdt:  decimal
breakdown:  array<{
    symbol:    string
    quantity:  decimal
    valueUsdt: decimal
}>
asOf:       timestamp-millis
```

## State stores

| Store | Type | Key | Value |
|---|---|---|---|
| `portfolio-valuation-holdings-store` | KTable | `userId|symbol` | `Holding(symbol, quantity)` |
| `portfolio-valuation-prices-store` | KTable | symbol | `BigDecimal priceUsdt` |
| `portfolio-valuation-position-value-store` | KTable | `userId|symbol` | `PositionValue` |
| `portfolio-value-store` | KTable | userId | `PortfolioValue` |

## Joins

- **Foreign-key table-table join** — holdings table × prices table on `symbol` → per-position value table.
- **Repartition + aggregate** — re-key position-value stream from `(userId, symbol)` to `userId`, then `groupByKey().aggregate(...)` to produce the portfolio total. This is the multiphase step.

## Windowing

N/A (continuous, non-windowed). Valuations are point-in-time.

## Processing guarantees

- **At-least-once** for the Kafka Streams app.
- Folds are commutative and associative: signed order quantities fold into holdings; latest price ticks materialise into a KTable.
- The existing Postgres-backed consumer still keeps its `ProcessedTransactionRepository` idempotency pattern (catalog file 30).

## Interactive queries

- `GET /portfolios/{userId}/streams-value` — latest USDT total and per-symbol breakdown from `portfolio-value-store`.
- `GET /portfolios/{userId}/value` remains the existing Postgres-backed comparison endpoint.
- Multi-instance metadata lookup and redirect is out of scope for the course dev stack; dev runs one portfolio-service replica.

## Reprocessing strategy (cross-cutting — candidate scope)

- Offset reset on both input topics under a fresh `application.id` rebuilds all state stores from scratch.
- Parallel-version strategy works too but is overkill here given the state volume.

## Implementation sketch (Kafka Streams DSL)

```java
KTable<String, BigDecimal> prices = builder
    .stream("crypto.price.raw", ...)
    .toTable()
    .mapValues(CryptoPriceUpdatedEvent::price);

KTable<String, Holding> holdings = builder
    .stream("transaction.order.approved", ...)
    .selectKey((k, v) -> UserSymbolKey.encode(v.userId(), v.symbol()))
    .mapValues(v -> new Holding(v.symbol().toUpperCase(), SignedQuantity.of(v)))
    .groupByKey()
    .aggregate(() -> new Holding("", BigDecimal.ZERO),
               (k, delta, current) -> new Holding(
                   delta.symbol(), current.quantity().add(delta.quantity())));

// Kafka Streams 3.x FK extractor is value-only, so Holding carries symbol.
KTable<String, PositionValue> positionValue = holdings
    .join(prices, Holding::symbol, (holding, price) -> value(holding, price));

KTable<String, PortfolioValue> portfolio = positionValue
    .groupBy((key, v) -> KeyValue.pair(UserSymbolKey.decode(key).userId(), v))
    .aggregate(() -> PortfolioValueAggregator.empty(""),
               (userId, pv, agg) -> PortfolioValueAggregator.add(userId, pv, agg, Instant.now()),
               (userId, pv, agg) -> PortfolioValueAggregator.subtract(userId, pv, agg, Instant.now()),
               Materialized.as("portfolio-value-store"));

portfolio.toStream().to("portfolio.value.updated", ...);
```

## Open decisions

All resolved by [ADR-0034](../adrs/0034_portfolio_valuation_streams_app.md):

- [x] Processing guarantee → **at_least_once** (folds are commutative and associative; retries cannot double-count).
- [x] Price input → **`crypto.price.raw`** (USDT-canonical; localisation stays at API read time per ADR-0028/0031).
- [x] Emit policy → emit on every change to the user's aggregate (no throttle; downstream consumers see the natural KTable changelog).
- [x] IQ host → **portfolio-service** (`GET /portfolios/{userId}/streams-value`); the existing `/value` Postgres endpoint stays for comparison.
- [x] State store vs Postgres for holdings → **both, parallel projections** of `transaction.order.approved`; the Streams store is authoritative for valuations, Postgres remains the system of record for the holding rows surfaced by the existing UI.
- [x] Signed quantity → centralised in `SignedQuantity.of(OrderApprovedEvent)`; today returns `+amount` (only buys exist). Sell flow only changes this helper.

## ADR candidates

Subsumed by [ADR-0034](../adrs/0034_portfolio_valuation_streams_app.md).

## Related scopes

- Input from: `02-price-stream-sanity.md`; existing `transaction.order.approved` topic.
- Reuse IQ plumbing for optional endpoint in scope 5.
