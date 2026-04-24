# 03. FX Price Enrichment

**Type:** stateful &nbsp;|&nbsp; **Required patterns:** Processing with External Lookup (Stream-Table Join) &nbsp;|&nbsp; **Owner:** TBD &nbsp;|&nbsp; **Status:** draft

## Purpose

Join `crypto.price.clean` (stream) with `reference.fx.rate` (KTable built from scope 1's compacted topic) to emit `crypto.price.localized`: every price tick carries its value in multiple fiat currencies.

## Why this matters

User-facing portfolios should show values in the user's local currency (CHF, EUR, GBP). Pre-computing in a stream removes FX conversion from request-path code and removes the HTTP API as a runtime dependency of the UI.

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

- [ ] Target currencies — hardcoded list, user-profile-driven, or both.
- [ ] One event with `map<ccy, price>` vs one event per target currency (fan-out).
- [ ] GlobalKTable vs regular KTable for FX (propose GlobalKTable — tiny volume).
- [ ] Expose IQ on this scope, or defer to scope 4?
- [ ] Consume `.clean` (propose) vs `.raw`.

## ADR candidates

- ADR — stream-table join layout (per-event fan-out vs map-value enrichment).
- ADR — GlobalKTable vs KTable for reference data.

## Related scopes

- Producers of input: `01-fx-rate-ingestion.md`, `02-price-stream-sanity.md`.
- Sibling: `04-portfolio-valuation.md` (also does stream-table joins, but for holdings).
