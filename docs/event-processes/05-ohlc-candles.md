# 05. OHLC Candlestick Aggregation

**Type:** windowed &nbsp;|&nbsp; **Required patterns:** Processing with Local State, Out-of-Sequence Events, Time Windows (Tumbling); optional Interactive Queries, Reprocessing &nbsp;|&nbsp; **Owner:** TBD &nbsp;|&nbsp; **Status:** draft

## Purpose

Aggregate `crypto.price.clean` into OHLC (open-high-low-close) bars per symbol for configurable intervals (1 min, 5 min, 1 h) using tumbling windows. Publish one topic per interval.

## Why this matters

Candlesticks are table-stakes UI for any crypto platform. They are also the textbook tumbling-window example, the natural feeder for scope 6 (indicators), and the most visual deliverable for the demo/report.

## Patterns hit

| File | Role |
|---|---|
| `07-time-windows.md` | tumbling windows per interval |
| `11-processing-with-local-state.md` | window state holds in-flight OHLC per symbol per window |
| `05-time-semantics.md` | event time (venue timestamp) drives window assignment |
| `16-out-of-sequence-events.md` | grace period absorbs late Binance ticks |
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
open:         double
high:         double
low:          double
close:        double
volume:       double
tickCount:    int
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

- Event time = Binance tick timestamp, extracted via a custom `TimestampExtractor`.
- Watermark advances with event time.
- Late events within grace merge into the window; events past grace are dropped (optionally routed to `crypto.ohlc.dropped` for observability).

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
                .withTimestampExtractor(new EventTimeExtractor()))
    .groupByKey()
    .windowedBy(TimeWindows.ofSizeAndGrace(
        Duration.ofMinutes(1), Duration.ofSeconds(10)))
    .aggregate(Ohlc::empty, Ohlc::update,
               Materialized.as("ohlc-1m-store"))
    .toStream()
    .map((wk, v) -> KeyValue.pair(
        wk.key() + "@" + wk.window().start(), v))
    .to("crypto.ohlc.1m", ...);
```

## Open decisions

- [ ] Intervals — 1m + 5m + 1h, or only 1m and derive the rest downstream?
- [ ] Grace-period values (proposals above).
- [ ] Emit on every window update, or suppress until window close?
- [ ] Route late-past-grace events to a dead-letter topic, or drop silently?
- [ ] Expose IQ? (Propose yes — lightweight.)
- [ ] Host this as part of portfolio-service, market-data-service, or a new streams app?

## ADR candidates

- ADR — OHLC intervals and grace-period policy.
- ADR — suppress vs emit-on-update.
- ADR — reprocessing strategy (offset reset vs parallel version).

## Related scopes

- Input from: `02-price-stream-sanity.md`.
- Feeds: `06-technical-indicators.md` (if indicators are computed from bars, the preferred design).
