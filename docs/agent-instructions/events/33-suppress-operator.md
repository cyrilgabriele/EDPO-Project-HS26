# Pattern: Suppress Operator (Suppressed Event Aggregator)

Windowed aggregations in Kafka Streams emit **continuously updated
results** — every time a new event arrives, the aggregate for its
window is re-emitted. This is fine for change-data-capture style
consumers, but wrong when downstream consumers expect a **single,
final** value per window (e.g. one closed candlestick, one finalised
session).

The `suppress` operator buffers intermediate updates and emits **only
the final result** when the window closes.

## Behaviour

- Buffers intermediate aggregates per window.
- Emits a single result event per window, after the window's grace
  period has elapsed (the window is sealed).
- Ideal for the **Suppressed Event Aggregator** pattern: provide final
  aggregation results, omit the intermediate noise.

```
Without suppress:
  group [8 2 4]   →   stream: 14 → 6 → 4    (each running aggregate)

With suppress:
  group [8 2 4]   →   stream: 14            (single final result)
```

## Requirements

- A **windowed**, grouped stream as input — windows are what define when
  a "final" result exists.
- **Buffering memory** — every open window holds its in-flight aggregate
  until close.
- A **strategy for buffer overflow** — what to do when memory is
  exhausted.

## API

```java
TimeWindows tumblingWindow =
    TimeWindows.ofSizeAndGrace(Duration.ofSeconds(60),
                               Duration.ofSeconds(5));

KTable<Windowed<String>, Long> pulseCounts =
    pulseEvents
        .groupByKey()
        .windowedBy(tumblingWindow)
        .count(Materialized.as("pulse-counts"))
        .suppress(Suppressed.untilWindowCloses(
            BufferConfig.unbounded().shutDownWhenFull()));
```

`Suppressed.untilWindowCloses(...)` is the canonical config: hold every
update, emit only on close.

`BufferConfig` choices:

- `unbounded().shutDownWhenFull()` — strict correctness, but kills the
  app on memory pressure. Good for small windows / low cardinality.
- `maxBytes(...)` / `maxRecords(...)` paired with
  `.emitEarlyWhenFull()` — bounded memory, sacrificing the
  "final-only" guarantee under load.

## When to use

- **OHLC candles** — one closed bar per `(symbol, window)`, not a
  continuous tape of partial bars.
- **Session summaries** — one record per closed session.
- **Periodic alerts** — fire once per window when a threshold is
  breached, not on every update inside the window.

## When *not* to use

- **Live dashboards** that already understand updates — emitting
  intermediates lets the UI show "current" values continuously.
- **Interactive queries** as the primary read path — the materialized
  store still updates continuously even if the *changelog* is
  suppressed; if downstream is purely IQ-driven, suppression buys
  nothing.
- **Hot windows with very long grace** — buffering cost can dominate.

## Trade-offs

- ✅ Single, clean output per window — easier downstream contract.
- ✅ Removes the noise of intermediate updates from output topics.
- ❌ Higher memory pressure (every open window held in buffer).
- ❌ Increased latency — nothing emits until the window closes.
- ❌ Buffer overflow needs a deliberate strategy.

## Relation to other patterns

- Pairs with **time windows** (`07-time-windows.md`) — suppression is
  meaningless without a window to close.
- Pairs with **out-of-sequence events** (`16-out-of-sequence-events.md`)
  — grace period determines *when* "the window closes" actually
  fires.

## Source

Lecture 10, HSG ICS. Tutorial: *Emit final results from a time window*
(Confluent).
