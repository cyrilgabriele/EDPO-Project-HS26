# Event Streaming Platform — Components

Conceptual model of an event streaming platform, end to end.

## Building Blocks

```
Event     Event   Event-at-a-time   Event   Event-at-a-time   Event
Source ─► Event ─► processing ─► storage ─► processing  ─►   Sink
                                    │
                                    │  (Event Stream ⇄ Table)
                                    │
                              Stream processing
```

### Event Source
Where events originate — sensors, applications, systems, business processes.

### Event
The individual record flowing through the platform (see event schema).

### Event-at-a-time Processing
Stateless or lightly-stateful per-event operations (filter, map, route).
Focus of Lecture 8 when discussing single-event processor types.

### Event Storage
Durable log (e.g. Kafka topics) retaining events for replay and processing.

### Event Stream ⇄ Table (duality)
The storage layer can be viewed either as an event stream (the log) or as a
materialized table (the latest-value-per-key view).

### Stream Processing
Stateful processing over windows and keys; joins, aggregations, interactive
queries are built here.

### Event Sink
Downstream consumer — database, dashboard, actuator, another application.

## Why the Split Matters

- **Event-at-a-time processing** → maps cleanly to simple
  producer/consumer code; stateless; easy to scale
- **Stream processing** → uses frameworks like Kafka Streams / ksqlDB;
  requires state, windowing, partitioning discipline

Lecture 8's *main focus* is event-at-a-time processing; stream processing
patterns (joins, aggregations, windowing) are discussed alongside.

## Source

Lecture 8, HSG ICS. Based partly on https://developer.confluent.io/patterns
