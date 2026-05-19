# 03. FX Price Enrichment

**Type:** stateful &nbsp;|&nbsp; **Required patterns:** Processing with External Lookup (Stream-Table Join) &nbsp;|&nbsp; **Owner:** Janni &nbsp;|&nbsp; **Status:** ready

## Purpose

Join `crypto.price.clean` (stream) with `reference.fx.rate` (KTable built from scope 1's compacted topic) to emit `crypto.price.localized`: every price tick carries its value in multiple fiat currencies.

## Why this matters

User-facing portfolios should show values in the user's Display Currency (USD, EUR, CHF, GBP). Pre-computing the conversion in a stream removes FX math from request-path code and removes the HTTP FX provider as a runtime dependency of the UI.

## Decisions locked in

- **Pattern shape:** stream-table join, broadcast. `LocalizedPrice` carries a `prices` map covering every currency in the supported set; consumers pick by user's Display Currency at API read time (no per-user routing). See [ADR-0030](../adrs/0030_stream_table_join_for_price_localization.md).
- **Serialization:** Avro + Confluent Schema Registry (see [ADR-0032](../adrs/0032_avro_schema_registry_for_derived_events.md)).
- **Host:** new streams module inside market-data-service (proposed; see Flagged ambiguities in [00-display-currency-cross-context.md](00-display-currency-cross-context.md)).
- **Cross-context contract:** documented in [00-display-currency-cross-context.md](00-display-currency-cross-context.md); the canonical `LocalizedPrice` field naming there overrides the schema sketch below if they conflict.

## Patterns hit

| File | Role |
|---|---|
| `13-stream-table-join.md` | core pattern — price stream × FX KTable |
| `06-state-and-stream-table-duality.md` | materialize compacted FX topic as GlobalKTable |
| `26-event-translator.md` | enrich event with localized values |
| `18-interactive-queries.md` | optional — latest localized price per symbol |

## Topology

```
crypto.price.clean ─────────┐
                            ▼
                ┌──────────────────────────────┐
                │ Stream-Table Join (per tick) │
                │ for each target ccy          │ ──►  crypto.price.localized
                │   lookup FxRate("USD"+ccy)   │
                └──────────────▲───────────────┘
                               │
                  reference.fx.rate (GlobalKTable, key = pair)
```

## Inputs

| Topic | Type | Key | Value |
|---|---|---|---|
| `crypto.price.clean` | KStream | symbol | `CleanPriceTick` |
| `reference.fx.rate`  | GlobalKTable | `"USDCHF"` | `FxRate` |

## Outputs

| Topic | Cleanup | Key | Value |
|---|---|---|---|
| `crypto.price.localized` | delete (default retention) | symbol | `LocalizedPrice` (Avro) |

### Avro — `ch.unisg.cryptoflow.shared.events.marketdata.LocalizedPrice`

```
symbol:          string
priceUsd:        double
localPrices:     map<string, double>    // {"EUR": 0.92, "CHF": 0.88}
sourceTimestamp: timestamp-millis
processedAt:     timestamp-millis
```

## State stores

| Store | Type | Key | Value |
|---|---|---|---|
| `fx-rates-global-store` | GlobalKTable (Rocks-DB backed) | currency pair | `FxRate` |

Auto-rehydrated from `reference.fx.rate` on startup; no partitioning concerns with GlobalKTable.

## Joins

- Type: stream × GlobalKTable lookup (equivalent to stream-table join).
- Join-key transform: `symbol → "USD" + targetCcy` for each target currency inside `mapValues`.
- Since a single price event must lookup *multiple* FX rows, the topology performs N lookups inside `mapValues` rather than emitting N events.

## Windowing

N/A — price events are point-in-time; FX table is latest-value.

## Processing guarantees

- At-least-once.
- Idempotent at consumers since enriched event key = input symbol and carries input `sourceTimestamp`.

## Interactive queries (optional)

- `GET /prices/localized/{symbol}` → latest `LocalizedPrice` from a materialized store on the output topic.

## Implementation sketch

```java
GlobalKTable<String, FxRate> fx = builder.globalTable("reference.fx.rate", ...);

builder.stream("crypto.price.clean", ...)
    .leftJoin(fx,
        (symbol, tick) -> "USD" + resolveTargetCcys(tick),      // placeholder
        (tick, rates) -> enrich(tick, rates, TARGET_CCYS))
    .to("crypto.price.localized", ...);
```

> In practice use `mapValues(tick -> enrich(tick, globalStore, TARGET_CCYS))` so a single event can carry multiple local prices.

## Open decisions

- [x] ~~Target currencies, hardcoded vs user-profile-driven.~~ → **hardcoded supported set** `{USD, EUR, CHF, GBP}` in the streams app config; user profile only governs the read-time pick ([ADR-0030](../adrs/0030_stream_table_join_for_price_localization.md), [ADR-0028](../adrs/0028_display_currency_as_user_identity_data.md)).
- [x] ~~One event with `map<ccy, price>` vs fan-out.~~ → **map-per-event** broadcast.
- [x] ~~GlobalKTable vs KTable.~~ → **GlobalKTable** for `reference.fx.rate` (tiny volume; no co-partitioning concern).
- [x] ~~Consume `.clean` vs `.raw`.~~ → **`crypto.price.clean`** (sanity-filtered input).
- [ ] Expose IQ on this scope? Defer to scope 4; revisit only if a dashboard read API needs `LocalizedPrice` without going through `PortfolioValue`.

## ADR candidates

Both originally-listed ADR candidates are subsumed by [ADR-0030](../adrs/0030_stream_table_join_for_price_localization.md).

## Related scopes

- Producers of input: `01-fx-rate-ingestion.md`, `02-price-stream-sanity.md`.
- Sibling: `04-portfolio-valuation.md` (also does stream-table joins, but for holdings).
