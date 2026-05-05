# Pattern: Processing with Local State

Aggregations require maintaining **state**. This pattern keeps that state
**local to each stream processor instance**.

## Core Idea

- **Stateful** processing with per-partition state
- Events with the **same key must be routed to the same partition**
- Each process maintains its own local state
- **Ordering guarantees hold within a partition**
- Each partition has **exactly one stream processor** consuming it

## Topology

```
Trades Topic                 Processors                 Aggregated trades topic
┌────────────┐      ┌──────────────────────────┐        ┌────────────┐
│ A–N part.  ├─────►│ Processor (local state)  ├───────►│ A–N part.  │
│            │      │ Aggregate min, avg       │        │            │
│ O–Z part.  ├─────►│ Processor (local state)  ├───────►│ O–Z part.  │
└────────────┘      │ Aggregate min, avg       │        └────────────┘
                    └──────────────────────────┘
```

State is partitioned the same way as the input data: one partition →
one task → one local state store.

## State Stores in Kafka Streams

Local state lives in **state stores**:

- **Embedded** — local to each instance, not a remote service. Low latency,
  no cross-instance contention; the trade-off is that state must be
  replicated for fault tolerance.
- **Default implementation: RocksDB** — fast, embedded key–value store.
- **Two flavors:**
  - *Persistent* — spills to disk; on failure, restores only missing
    updates from the changelog topic (faster recovery).
  - *In-memory* — full state must be rebuilt from the changelog on restart.
- **Fault-tolerant via changelog topics** — every state-store update is
  also written to a Kafka changelog. On failure the store is rebuilt by
  replaying it.
- **Optional standby replicas** — keep warm copies of state on other
  instances to shorten failover time.

## Grouping Before Aggregation (Event Grouper pattern)

Stateful aggregation needs the input *grouped* so that all events for the
same key reach the same task.

| Source  | Operators                       | Notes                                  |
|---------|---------------------------------|----------------------------------------|
| KStream | `groupByKey()` (preferred)      | Uses existing key, no repartition      |
|         | `groupBy(KeyValueMapper)`       | May change the key → triggers shuffle  |
| KTable  | `groupBy(KeyValueMapper)` only  | Always defines a new key               |

The output of grouping is a `KGroupedStream` or `KGroupedTable` — an
intermediate shape that allows aggregation. Grouping itself is a
stateless transformation; the aggregation that follows is what is
stateful.

## Aggregation (Event Aggregator pattern)

Three operators in increasing generality:

- **`count`** — count events per key
- **`reduce`** — combine values; *output type = input type*
- **`aggregate`** — fully custom; *output type may differ from input type*

Aggregation has two parts:

- **Initializer** — starting value for a fresh key
- **Adder** — fold the next event into the running aggregate

Tables also need a **Subtractor** — when a value is updated, the previous
contribution must be removed before the new one is added. Streams are
append-only and do not need a subtractor.

```java
KTable<String, HighScores> highScores =
    grouped.aggregate(
        HighScores::new,                              // initializer
        (key, value, agg) -> agg.add(value),          // adder
        Materialized.<String, HighScores, KeyValueStore<Bytes, byte[]>>
            as("leader-boards")
            .withKeySerde(Serdes.String())
            .withValueSerde(JsonSerdes.HighScores()));
```

Naming the store via `Materialized.as("…")` makes it queryable from
outside the topology — see `18-interactive-queries.md`.

## Challenges

- **Memory usage** — state grows with key cardinality
- **Persistence** — state must survive restarts (changelog topics in Kafka
  Streams)
- **Rebalancing** — when partitions move between instances, state must be
  rebuilt or migrated

## When Things Get Complicated

- Local state is fine for per-key aggregates (min, max, avg per symbol)
- It **gets complicated when you need global information** across partitions
  — that requires multiphase processing / repartitioning
  (`12-multiphase-repartitioning.md`)

## Trade-offs

- ✅ Low-latency stateful computation without external stores
- ✅ Ordering per-key preserved via partitioning
- ❌ Global aggregates need a second phase
- ❌ Rebalances temporarily degrade availability

## Source

Lectures 8 & 9, HSG ICS.
