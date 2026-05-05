# 04. Real-Time Portfolio Valuation

**Type:** stateful &nbsp;|&nbsp; **Required patterns:** Stream-Table Join, Table-Table Join, Processing with Local State, Multiphase Repartitioning, Interactive Queries &nbsp;|&nbsp; **Owner:** TBD &nbsp;|&nbsp; **Status:** draft

## Purpose

Continuously compute each user's total portfolio value (sum of `qty × current price` across all holdings) and expose it via Kafka Streams interactive queries so the portfolio dashboard can read it without hitting PostgreSQL.

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
 holdings KTable                         crypto.price.clean
 key: (userId, symbol)                         │
 value: qty                                    ▼ (toTable, keep latest per symbol)
       │                                 prices KTable
       │                                 key: symbol
       └──────────── FK join on symbol ──────┘
                           │
                           ▼ (qty × priceUsd)
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
 (key = userId)                  GET /portfolio/{user}/value
                                 GET /portfolio/{user}/breakdown
```

## Inputs

| Topic | Type | Key | Value |
|---|---|---|---|
| `transaction.order.approved` | KStream | userId (or orderId) | existing `OrderApprovedEvent` |
| `crypto.price.clean`         | KStream | symbol | `CleanPriceTick` |

## Outputs

| Topic | Cleanup | Key | Value |
|---|---|---|---|
| `portfolio.value.updated` | compact | userId | `PortfolioValue` (Avro) |

### Avro — `ch.unisg.cryptoflow.shared.events.portfolio.PortfolioValue`

```
userId:     string
totalUsd:   double
breakdown:  array<{
    symbol:   string
    qty:      double
    priceUsd: double
    valueUsd: double
}>
asOf:       timestamp-millis
```

## State stores

| Store | Type | Key | Value |
|---|---|---|---|
| `holdings-store`        | KTable | `(userId, symbol)` | `double qty` |
| `latest-price-store`    | KTable | symbol | `double priceUsd` |
| `position-value-store`  | KTable | `(userId, symbol)` | `double valueUsd` |
| `portfolio-value-store` | KTable | userId | `PortfolioValue` |

## Joins

- **Foreign-key table-table join** — holdings table × prices table on `symbol` → per-position value table.
- **Repartition + aggregate** — re-key position-value stream from `(userId, symbol)` to `userId`, then `groupByKey().aggregate(...)` to produce the portfolio total. This is the multiphase step.

## Windowing

N/A (continuous, non-windowed). Valuations are point-in-time.

## Processing guarantees

- At-least-once by default.
- **Exactly-once-v2 recommended** for this app to prevent double-counting positions on retries.
- If the app also writes to Postgres, keep the existing `ProcessedTransactionRepository` idempotency pattern (catalog file 30).

## Interactive queries

- `GET /portfolio/{userId}/value` — latest `totalUsd`.
- `GET /portfolio/{userId}/breakdown` — per-position detail.
- Implement the standard "metadata lookup + redirect" so any instance can serve any key during rebalance.

## Reprocessing strategy (cross-cutting — candidate scope)

- Offset reset on both input topics under a fresh `application.id` rebuilds all state stores from scratch.
- Parallel-version strategy works too but is overkill here given the state volume.

## Implementation sketch (Kafka Streams DSL)

```java
KTable<String, Double> prices = builder
    .stream("crypto.price.clean", ...)
    .toTable()
    .mapValues(CleanPriceTick::priceUsd);

KTable<UserSymbol, Double> holdings = builder
    .stream("transaction.order.approved", ...)
    .groupBy((k, v) -> new UserSymbol(v.userId(), v.symbol()))
    .aggregate(() -> 0.0, (k, order, qty) -> qty + signedQty(order));

// FK extractor needs access to the holdings KEY (symbol lives in the
// composite key, not the value), so use the BiFunction<K, V, KO> overload.
KTable<UserSymbol, Double> positionValue = holdings
    .join(prices, (us, qty) -> us.symbol(), (qty, price) -> qty * price);

KTable<String, PortfolioValue> portfolio = positionValue
    .groupBy((us, v) -> KeyValue.pair(us.userId(), entry(us.symbol(), v)))
    .aggregate(PortfolioValue::empty,
               PortfolioValue::add, PortfolioValue::subtract,
               Materialized.as("portfolio-value-store"));

portfolio.toStream().to("portfolio.value.updated", ...);
```

## Open decisions

- [ ] Processing guarantee — at-least-once vs exactly-once-v2 (propose EoS).
- [ ] Consume `crypto.price.clean` (propose) vs `crypto.price.raw`.
- [ ] Emit per-tick value, or throttle (only on material change > 0.1%)?
- [ ] IQ host — portfolio-service (propose) vs dedicated streams service.
- [ ] State store vs Postgres — authoritative source for holdings? Propose state store with Postgres as an optional read-model.
- [ ] Handle *signed* quantity from orders — buy = +qty, sell = −qty.

## ADR candidates

- ADR — processing guarantees for portfolio app.
- ADR — state store vs Postgres for holdings.
- ADR — IQ endpoint placement and rebalance-safe lookup strategy.

## Related scopes

- Input from: `02-price-stream-sanity.md`; existing `transaction.order.approved` topic.
- Reuse IQ plumbing for optional endpoint in scope 5.
