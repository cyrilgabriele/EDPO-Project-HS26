# Time Windows

Windows define **how we group events in time**. Most stateful operations —
moving averages, joins, aggregations — depend on a window. Choice of
window type and time semantics determines the result.

## Key Parameters

- **Size** — the duration the window spans
- **Advance / hop** — how often the window moves (overlapping windows)
- **Inactivity gap** — for session windows, the silence that closes a
  session
- **Grace period** — how long the window remains updatable for late /
  out-of-order events

## Five Window Types

Lecture 10 widens the model from three to five window types. Choice of
window type affects the result.

### 1. Tumbling window *(time-based)*
Fixed-size, **non-overlapping**, **gap-less**. Boundaries are aligned with
the epoch and aligned across keys. Each event belongs to exactly **one**
window.

```
|--size--|--size--|--size--|
[ win n ][ win n+1 ][ win n+2 ]   events →
```

Use case: per-minute aggregates, hourly counters, **OHLC bars**.

```java
TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(5));
TimeWindows.ofSizeAndGrace(Duration.ofSeconds(60),
                           Duration.ofSeconds(5));
```

### 2. Hopping window *(time-based)*
Fixed-size, **may overlap**. Defined by size **and** advance interval
(advance < size → overlap). The same event may belong to multiple
windows. Boundaries are epoch-aligned.

```
[ win n       ]
   [ win n+1       ]
      [ win n+2       ]
advance | ── duration ── |
```

Use case: rolling moving averages emitted at a fixed cadence (e.g. SMA-20
recomputed every 1 minute).

```java
TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(5))
           .advanceBy(Duration.ofSeconds(4));
```

### 3. Session window *(session-based)*
**Variable-sized**, **data-driven**. A session closes after an
**inactivity gap** Δt larger than the configured threshold. Boundaries
are unaligned across keys; bounds are inclusive of the records that
define them.

```
[ session n ]    Δt > inactivity gap    [ session n+1 ]
```

Use case: per-user web/app sessions where session length varies.

```java
SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(5));
```

### 4. Sliding join window *(NEW — joins only)*
Fixed-size window used by `KStream`–`KStream` joins via the `JoinWindows`
class. Two records (one per stream, same key) are joined **iff their
timestamps differ by ≤ window size**. Conceptually a "centred" window
around each record on each side.

```java
JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5));
JoinWindows.ofTimeDifferenceAndGrace(Duration.ofSeconds(60),
                                     Duration.ofSeconds(10));
```

Use case: trade-tape × order-book join, click × search join. See
`15-streaming-join.md`.

### 5. Sliding aggregation window *(NEW — aggregations only)*
**No fixed buckets** — a window is defined per record. Events are
grouped if their timestamps fall within the window size. Produces fully
continuous, fine-grained aggregates rather than one snapshot per
bucket.

```java
SlidingWindows.ofTimeDifferenceAndGrace(Duration.ofSeconds(5),
                                        Duration.ofSeconds(0));
```

Use case: continuous moving averages where every record should re-emit
the rolling aggregate (more responsive than a hopping window with small
advance).

## Selecting a window

A worked example from the lecture — converting raw pulse events into a
heart rate via a windowed aggregation:

| Type | Verdict |
|---|---|
| **Tumbling** | ✅ fixed 60 s buckets |
| Hopping | overlap not needed |
| Session | variable size — wrong shape |
| Sliding join | for joins only |
| Sliding aggregation | no fixed buckets — wrong shape |

> Pick the window from the *shape of the result you want*: aligned
> buckets → tumbling; rolling snapshot at fixed cadence → hopping;
> activity-driven → session; pairwise temporal join → sliding join;
> per-record continuous aggregate → sliding aggregation.

## Window aggregations in Kafka Streams

```java
TimeWindows tumblingWindow =
    TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(60));

KTable<Windowed<String>, Long> pulseCounts =
    pulseEvents
        .groupByKey()
        .windowedBy(tumblingWindow)
        .count(Materialized.as("pulse-counts"));
```

Notes:

- The KTable key changes from `String` → `Windowed<String>`.
- **Results are emitted whenever a new event arrives** — the result is
  *continuously updated per window* until the window closes (see
  *Emitting window results* below).

## Emitting window results

When should window results actually be emitted? More complex than it
looks:

- Unbounded streams may **not** be in timestamp order. Kafka guarantees
  order **per partition (offset order)**, *not* by event time.
- Late events can arrive after later ones (**out-of-order**).
- Tradeoff:
  - Wait longer → more complete results.
  - Emit earlier → lower latency.
- **Results may change over time as late events arrive.**

Two levers:

1. **Grace period** — keep the window open for late events. Other systems
   call the equivalent mechanism a *watermark* (e.g. Flink). See
   `16-out-of-sequence-events.md`.
2. **Suppress** — buffer intermediate updates and only emit the final
   result when the window closes. See
   [`33-suppress-operator.md`](33-suppress-operator.md).

```java
TimeWindows.ofSizeAndGrace(Duration.ofSeconds(60),
                           Duration.ofSeconds(5));
```

## Time-driven data flow

In Kafka Streams, **time controls processing order, not arrival order**.

- Each stream task owns one *partition group* across its co-partitioned
  inputs.
- The task compares the **head record** of every input partition and
  picks the one with the **lowest timestamp** to process next
  (`PartitionGroup.nextRecord`).
- Records inside the task are effectively merged into a single stream
  ordered by timestamp, *not* by arrival.

> Processing order ≈ timestamp order. This is what makes joins and
> aggregations across multiple inputs deterministic even when inputs
> produce events at different rates.

## Output timestamps

- Output records carry a timestamp, **usually derived from input
  events**.
- For **joins**, the output timestamp is `max(input timestamps)`.
- For aggregations, the output inherits the timestamp of the latest
  contributing input.

## Querying windowed stores

Windowed stores key entries by **(original key, time window)**. Three
shapes of query:

- Lookup by key within a time range (`fetch(key, from, to)`).
- Scan keys within a time range.
- Iterate over all entries.

> A time range is **required** for key-based queries against a windowed
> store — there is no plain `get(key)`.

See `18-interactive-queries.md` for the IQ side.

## Source

Lectures 7 and 10, HSG ICS. Window definitions also from ksqlDB docs:
https://docs.ksqldb.io/en/latest/concepts/time-and-windows-in-ksqldb-queries/
