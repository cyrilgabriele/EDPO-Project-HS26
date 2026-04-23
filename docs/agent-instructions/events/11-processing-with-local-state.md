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

## Trade-offs

- ✅ Low-latency stateful computation without external stores
- ✅ Ordering per-key preserved via partitioning
- ❌ Global aggregates need a second phase
- ❌ Rebalances temporarily degrade availability

## Source

Lecture 8, HSG ICS.
