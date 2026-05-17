# 05. OHLC Candlestick Aggregation

**Type:** windowed &nbsp;|&nbsp; **Required patterns:** Processing with Local State, Out-of-Sequence Events, Time Windows (Tumbling); optional Interactive Queries, Reprocessing &nbsp;|&nbsp; **Owner:** Janni &nbsp;|&nbsp; **Status:** ready

## Purpose

Aggregate `crypto.price.clean` into OHLC (open-high-low-close) bars per symbol for configurable intervals (1 min, 5 min, 1 h) using tumbling windows. Publish one topic per interval.

## Why this matters

Candlesticks are table-stakes UI for any crypto platform. They are also the textbook tumbling-window example, the natural feeder for scope 6 (indicators), and the most visual deliverable for the demo/report.

## Decisions locked in

- **Currency:** bars are venue-native (USDT) on a single topic per interval. Display-currency conversion happens at API read time on the consuming endpoint. See [ADR-0031](../adrs/0031_venue_native_ohlc_with_read_time_conversion.md).
- **Emission:** `suppress(untilWindowCloses)`, one final bar per `(symbol, window)`. No `.live` sibling topic for now.
- **Serialization:** Avro per [ADR-0032](../adrs/0032_avro_schema_registry_for_derived_events.md).
- **Volume field dropped.** `CleanPriceTick` carries no volume (scope 02 omits it; Binance tick stream doesn't expose it), so the `Ohlc` schema below uses **`tickCount` only** as the volume proxy. Removed `volume` from the original draft.
- **Coin metadata enrichment.** Closed bars are joined with `reference.crypto.metadata` (a `GlobalKTable` produced by coin-metadata-service) inside the same topology. The Ohlc schema carries the joined fields (`name`, `imageUrl`, `marketCapRank`, etc.) added backward-compatibly. See [ADR-0033](../adrs/0033_coin_metadata_enrichment_via_global_ktable.md).

## Patterns hit

| File | Role |
|---|---|
| `07-time-windows.md` | tumbling windows per interval |
| `11-processing-with-local-state.md` | window state holds in-flight OHLC per symbol per window |
| `05-time-semantics.md` | event time (venue timestamp) drives window assignment |
| `32-timestamp-extractor.md` | custom extractor pulls `sourceTimestamp` from `CleanPriceTick` payload |
| `16-out-of-sequence-events.md` | grace period absorbs late Binance ticks |
| `33-suppress-operator.md` | emit one final bar per window — see Window emission below |
| `18-interactive-queries.md` | optional — latest closed bar per symbol |
| `17-reprocessing.md` | optional — demoed here via offset reset or parallel-version |

## Topology

```
crypto.price.clean (KStream, key = symbol)
       │
       ▼
  groupByKey(symbol)
       │
       ▼
  windowedBy( Tumbling(1 min), grace = 10 s )
       │
       ▼
  aggregate( OHLC init + update per tick )
       │
       ▼  toStream + remap (Windowed<String> → flat key)
  crypto.ohlc.1m             (analogous pipelines for 5m and 1h)
```

## Inputs

| Topic | Type | Key | Value |
|---|---|---|---|
| `crypto.price.clean` | KStream | symbol | `CleanPriceTick` |

## Outputs

| Topic | Cleanup | Key | Value |
|---|---|---|---|
| `crypto.ohlc.1m` | delete (7d) | `"symbol@windowStartMillis"` | `Ohlc` |
| `crypto.ohlc.5m` | delete (30d) | same | same |
| `crypto.ohlc.1h` | delete (365d) | same | same |

### Avro — `ch.unisg.cryptoflow.shared.events.marketdata.Ohlc`

```
symbol:       string
intervalSec:  int
windowStart:  timestamp-millis
windowEnd:    timestamp-millis
open:         double                  // USDT
high:         double                  // USDT
low:          double                  // USDT
close:        double                  // USDT
tickCount:    int                     // volume proxy; see Decisions locked in
```

## State stores

| Store | Type | Key | Value |
|---|---|---|---|
| `ohlc-1m-store` | WindowStore | `symbol` | `Ohlc` (in-flight window) |
| `ohlc-5m-store` | WindowStore | same | same |
| `ohlc-1h-store` | WindowStore | same | same |

Window-store retention: 2× window size + grace.

## Joins

N/A.

## Windowing

| Interval | Size | Grace | Store retention |
|---|---|---|---|
| 1 min | 1 min | 10 s | ~2 min |
| 5 min | 5 min | 30 s | ~10 min |
| 1 h | 1 h | 2 min | ~2 h |

## Time semantics

- Event time = Binance tick timestamp on `CleanPriceTick`. The producer
  in scope 2 already stamps `sourceTimestamp`; OHLC reads it via a
  custom `TimestampExtractor` (see `32-timestamp-extractor.md`) rather
  than relying on the Kafka record metadata. Reprocessing then yields
  identical bars regardless of when it runs.
- Kafka Streams orders the input by extracted timestamp (time-driven
  data flow), so windows fill deterministically per symbol.
- Late events within grace merge into the window; events past grace are
  dropped (optionally routed to `crypto.ohlc.dropped` for
  observability).

## Window emission — emit on every update vs. suppress

Two valid emission modes; **propose `suppress` until window close** as
the default contract for `crypto.ohlc.{interval}`:

- **Suppress (proposed)** — one event per `(symbol, window)`, emitted
  after the window's grace period. Matches the trading semantics of a
  "closed candle". Downstream (#6 indicators, UI) get a clean,
  single-shot bar per window.
- **Emit on every update** — keeps the bar live as ticks arrive. Useful
  if a UI wants a streaming "current bar" feed; cheaper to add later as
  a separate `crypto.ohlc.{interval}.live` topic than to push the
  filtering onto every consumer.

See `33-suppress-operator.md` for the buffer-config trade-off
(`unbounded().shutDownWhenFull()` vs `maxBytes(...).emitEarlyWhenFull()`).
Per-symbol window count is bounded (≈ 30–50 symbols × 2 open windows ×
3 intervals ≪ 1k entries), so unbounded buffering is safe.

## Processing guarantees

- At-least-once is sufficient — windowed aggregation is tolerant of retries because state is keyed by `(symbol, windowStart)`.

## Interactive queries (optional, recommended)

- `GET /ohlc/{symbol}/{interval}?n=N` → last N closed bars from the window store. Cheap to add if scope 4 already wires IQ plumbing.

## Reprocessing strategy (pattern 17 candidate)

- **Offset reset** — under a fresh `application.id`, seek to beginning of `crypto.price.clean`; all historical bars recompute.
- **Parallel version** — run a second app writing to `crypto.ohlc.1m.v2` alongside v1, flip the UI when satisfied.

Committing a reprocessing runbook here covers the required pattern 17.

## Implementation sketch

```java
builder.stream("crypto.price.clean",
        Consumed.with(Serdes.String(), cleanSerde)
                .withTimestampExtractor(new CleanPriceTickTimestampExtractor()))
    .groupByKey()
    .windowedBy(TimeWindows.ofSizeAndGrace(
        Duration.ofMinutes(1), Duration.ofSeconds(10)))
    .aggregate(Ohlc::empty, Ohlc::update,
               Materialized.as("ohlc-1m-store"))
    .suppress(Suppressed.untilWindowCloses(
        BufferConfig.unbounded().shutDownWhenFull()))     // one bar per window
    .toStream()
    .map((wk, v) -> KeyValue.pair(
        wk.key() + "@" + wk.window().start(), v))
    .to("crypto.ohlc.1m", ...);
```

## Open decisions

- [x] Emit on every window update, or suppress until window close? → **suppress until window close** ([ADR-0031](../adrs/0031_venue_native_ohlc_with_read_time_conversion.md)). Optional sibling `.live` topic if the UI ever needs streaming bars; not built for now.
- [x] ~~Per-currency OHLC vs venue-native?~~ → **venue-native USDT** ([ADR-0031](../adrs/0031_venue_native_ohlc_with_read_time_conversion.md)).
- [x] ~~Avro vs JSON.~~ → **Avro + Schema Registry** ([ADR-0032](../adrs/0032_avro_schema_registry_for_derived_events.md)).
- [ ] Intervals: 1m + 5m + 1h (proposed), or only 1m and derive the rest downstream? Default: 1m + 5m + 1h.
- [ ] Grace-period values: defaults in the table above stand unless the demo shows otherwise.
- [ ] Route late-past-grace events to a dead-letter topic, or drop silently? Propose **drop with a counter** for now; add DLT only if the demo needs it.
- [ ] Expose IQ? Propose **yes**, with `GET /ohlc/{symbol}/{interval}?n=N`, reusing the IQ plumbing scope 4 wires up.
- [ ] Host this as part of portfolio-service, market-data-service, or a new streams app? Propose **market-data-service** (transforms market data, sibling to scope-03's enrichment app).

## ADR candidates

- ~~ADR for OHLC intervals and grace-period policy.~~ → operational, not architecturally load-bearing; documented inline above.
- ~~ADR for reprocessing strategy (offset reset vs parallel version).~~ → demonstrate offset reset as the primary strategy; document the runbook inline, no ADR needed.

## Related scopes

- Input from: `02-price-stream-sanity.md`.
- Feeds: `06-technical-indicators.md` (if indicators are computed from bars, the preferred design).
