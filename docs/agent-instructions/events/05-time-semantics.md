# Time Semantics

Time is arguably the most important concept in stream processing. Stream
applications frequently perform operations over **time windows**, so having a
common notion of time is critical. There are different notions of time, and
**the chosen time semantics determines the correctness of windows, joins, and
aggregations**.

> Recommended reading on the complexity of time in distributed systems:
> Jay Kreps, *"There is No Now"*.

## Three Time Semantics

### 1. Event time *(typically most relevant)*
- Time when the event we are tracking **occurred** at the data source
- Timestamp can be **embedded in the payload** or set explicitly by the
  Kafka producer
- Preferred because it reflects real-world ordering of facts and survives
  reprocessing unchanged

### 2. Log append time / Ingestion time
- Time when the event **arrived at the Kafka broker** and was appended to
  the topic
- Set by the broker, not by the producer
- Used as a fallback **when event time cannot be used**

### 3. Processing time (a.k.a. wall-clock time)
- Time the stream processing application **received the event** to perform
  some calculation
- Can be many minutes or even hours **after** event time
- **Reprocessing assigns new processing timestamps** — same input, different
  results
- Least reliable for correctness; most reliable for "now"-style operations

## Two Processing Patterns

The lecture frames the choice as two complementary processor patterns:

- **Event-time Processing** — every event in the stream carries its
  original creation timestamp; the application reads that timestamp and
  processes events along their original timeline. Preserves real-world
  order even when arrival order does not.
- **Wall-clock Processing** — the application uses (1) the time the event
  was created, (2) the time it arrived on the stream, or (3) a value
  derived from the payload, depending on what is available. Depends on
  *when* data is processed and **can re-order events**.

Use event-time processing when correctness depends on real-world ordering
(financial bars, sensor windows, joins). Use wall-clock processing when
"now" semantics dominate (alerting on liveness, simple counters).

## Kafka Configuration

Kafka can stamp messages on the producer side. Two relevant configs:

- `log.message.timestamp.type` — broker level
- `message.timestamp.type` — **topic level** (overrides the broker setting)

Two values:

- `CreateTime` — producer-side timestamp → **event-time semantics**
- `LogAppendTime` — broker-side timestamp → ingestion-time semantics

> Set `CreateTime` when you want event-time semantics downstream.

## Choosing Time in Kafka Streams — TimestampExtractor

In Kafka Streams, **the active time semantics are decided by the
`TimestampExtractor` registered on each input stream**, not by the broker
config alone. The extractor decides which value (record metadata,
ingestion time, payload field, wall clock) is used as the record's
timestamp for window assignment, joins, and aggregations.

See [`32-timestamp-extractor.md`](32-timestamp-extractor.md) for the
interface, the built-in extractors, the standard custom-extractor pattern
(read a timestamp from the payload), and how to register one via
`Consumed.withTimestampExtractor(...)`.

## Practical Implications

- Windowed operations (joins, aggregations, moving averages) must specify
  *which* time they operate on — results differ significantly.
- Out-of-order events (event time < ingestion time by more than expected)
  require a **grace period** to be handled correctly (see
  `16-out-of-sequence-events.md`).
- Processing time is vulnerable to backlog, restarts, and reprocessing — it
  is **not** a stable basis for business semantics.
- **Time controls processing order, not arrival order** — Kafka Streams
  synchronises input partitions and emits the record with the lowest
  timestamp first. See `07-time-windows.md` § *Time-driven data flow*.

## Source

Lectures 7 and 10, HSG ICS.
