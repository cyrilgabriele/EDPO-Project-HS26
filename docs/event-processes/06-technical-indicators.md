# 06. Technical Indicators (SMA / EMA / RSI)

**Type:** windowed &nbsp;|&nbsp; **Required patterns:** Processing with Local State, Time Windows (Hopping); optional Multiphase Repartitioning, Out-of-Sequence Events &nbsp;|&nbsp; **Owner:** TBD &nbsp;|&nbsp; **Status:** draft

## Purpose

Compute classical technical indicators (Simple Moving Average, Exponential Moving Average, Relative Strength Index, optionally MACD/Bollinger) per symbol using hopping windows. Publish to a shared indicator topic carrying the latest snapshot.

## Why this matters

Hopping windows demonstrate *rolling* aggregations — different from scope 5's tumbling windows. Indicators are what retail trading dashboards surface; pair visually with OHLC charts.

## Patterns hit

| File | Role |
|---|---|
| `07-time-windows.md` | hopping windows |
| `11-processing-with-local-state.md` | window state for moving aggregates and gain/loss buffers |
| `12-multiphase-repartitioning.md` | applies in option A — OHLC is already per-symbol, but a later cross-symbol ranking (e.g. top-RSI) would be the multiphase step |
| `05-time-semantics.md` | event time drives window assignment |
| `16-out-of-sequence-events.md` | grace period |

## Topology — Option A (from bars, preferred)

```
crypto.ohlc.1m  ──►  groupByKey(symbol)
                         │
                         ▼
                  windowedBy( Hopping(size = 20 m, advance = 1 m), grace = 30 s )
                         │
                         ▼
                    aggregate( rolling closes, gains, losses, EMA running value )
                         │
                         ▼
                    mapValues( derive SMA20, EMA20, RSI14 )
                         │
                         ▼
                   crypto.indicator.snapshot
```

## Topology — Option B (from ticks)

Same shape with input `crypto.price.clean`; higher throughput and more CPU. Only pick B if scope 5 is not adopted.

## Inputs

| Topic | Option | Type | Key | Value |
|---|---|---|---|---|
| `crypto.ohlc.1m` | A | KStream | `symbol@windowStart` | `Ohlc` |
| `crypto.price.clean` | B | KStream | symbol | `CleanPriceTick` |

## Outputs

| Topic | Cleanup | Key | Value |
|---|---|---|---|
| `crypto.indicator.snapshot` | delete (30d) | symbol | `IndicatorSnapshot` (Avro) |

### Avro — `ch.unisg.cryptoflow.shared.events.marketdata.IndicatorSnapshot`

```
symbol:     string
asOf:       timestamp-millis
sma20:      double
ema20:      double
rsi14:      double
// extendable: macd, bollinger, ...
```

## State stores

| Store | Type | Key | Value |
|---|---|---|---|
| `indicator-window-store` | WindowStore | symbol | `RollingIndicatorState` (closes, gains, losses, running EMA) |

## Joins

N/A (within a single symbol).

## Windowing

- Type: hopping.
- Size: 20 bars (20 min on 1m OHLC) → SMA20 / EMA20. RSI uses 14 bars, same window with internal truncation.
- Advance: 1 bar (1 min) → one emission per symbol per minute.
- Grace: 30 s.
- Retention: 2× window size.

## Time semantics

- Inherits event time from the source (OHLC windowStart for option A, tick timestamp for option B).

## Processing guarantees

- At-least-once. Idempotent via `(symbol, windowStart)` keying.

## Interactive queries (optional)

- `GET /indicators/{symbol}` → latest snapshot from the output store.

## Implementation sketch

```java
builder.stream("crypto.ohlc.1m", ...)
    .groupByKey()
    .windowedBy(TimeWindows.ofSizeAndGrace(
        Duration.ofMinutes(20), Duration.ofSeconds(30))
        .advanceBy(Duration.ofMinutes(1)))
    .aggregate(RollingIndicatorState::empty,
               RollingIndicatorState::update,
               Materialized.as("indicator-window-store"))
    .toStream()
    .mapValues(RollingIndicatorState::toSnapshot)
    .to("crypto.indicator.snapshot", ...);
```

## Open decisions

- [ ] Input source — bars (A, propose) vs ticks (B).
- [ ] One event per indicator (separate topics) vs one snapshot with all of them (single topic, propose).
- [ ] Indicator set — SMA + EMA + RSI minimum; MACD / Bollinger optional.
- [ ] Window sizes — 20 only, or 20 / 50 / 200 combos?
- [ ] Expose IQ?

## ADR candidates

- ADR — indicator input source (bars vs ticks).
- ADR — indicator event layout (single snapshot vs per-indicator topics).

## Related scopes

- Input from: `05-ohlc-candles.md` (option A) or `02-price-stream-sanity.md` (option B).
