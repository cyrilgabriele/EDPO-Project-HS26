# Time Semantics

Time is arguably the most important concept in stream processing. Stream
applications frequently perform operations over **time windows**, so having a
common notion of time is critical. There are different notions of time.

> Recommended reading on the complexity of time in distributed systems:
> Jay Kreps, *"There is No Now"*.

## Three Time Semantics

### 1. Event time *(typically most relevant)*
- Time when the event we are tracking **occurred** and the record was
  **created by the data source**
- Preferred because it reflects real-world ordering of facts

### 2. Log append time / Ingestion time
- Time when the event **arrived at the Kafka broker** (ingestion time)
- Used as a fallback **when event time cannot be used**

### 3. Processing time
- Time the stream processing application **received the event** to perform
  some calculation
- Can be many minutes or even hours **after** event time
- Least reliable for correctness; most reliable for "now"-style operations

## Practical Implications

- Windowed operations (joins, aggregations, moving averages) must specify
  *which* time they operate on — results differ significantly
- Out-of-order events (event time < ingestion time by more than expected)
  require a **grace period** to be handled correctly (see out-of-sequence pattern)
- Processing time is vulnerable to backlog, restarts, and reprocessing — it
  is **not** a stable basis for business semantics

## Source

Lecture 7, HSG ICS.
