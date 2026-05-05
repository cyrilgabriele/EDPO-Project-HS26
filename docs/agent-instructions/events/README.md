# Event-Driven & Process-Oriented Architectures — Reference Notes

Reference notes derived from HSG ICS lectures 7–9 on event processing.
Each file is a focused, standalone reference. Lecture 9 (stateful
processing) extends files 06, 11, 12, 13, 14, 15, and 18 with state
stores, KTable / GlobalKTable, join operators, co-partitioning,
grouping/aggregation, and materialized stores for interactive queries.

## Lecture 7 — Stream Processing: Intro & Key Concepts

- **01-events-and-streams.md** — events, unbounded streams, event schema
- **02-event-processing-architecture.md** — producers/consumers, EPN
- **03-processing-paradigms.md** — request–response vs batch vs stream; DB vs stream
- **04-topology.md** — stream topology; CryptoSentiment example
- **05-time-semantics.md** — event, log append, processing time
- **06-state-and-stream-table-duality.md** — stateless/stateful; shoe-inventory example
- **07-time-windows.md** — tumbling, hopping, session windows
- **08-processing-guarantees.md** — at-most-once, at-least-once, exactly-once

## Lecture 8 — Stream Processing Design Patterns

### Overview
- **09-patterns-overview.md** — pattern catalog + project requirements

### Core processing patterns (minimal project requirements)
- **10-single-event-processing.md** — stateless map/filter per event
- **11-processing-with-local-state.md** — per-partition stateful aggregation
- **12-multiphase-repartitioning.md** — local + global aggregation (top-N)
- **13-stream-table-join.md** — external lookup with CDC-cached table

### Additional processing patterns
- **14-table-table-join.md** — equi-join and foreign-key join
- **15-streaming-join.md** — windowed stream–stream join
- **16-out-of-sequence-events.md** — grace period for late events
- **17-reprocessing.md** — parallel new version vs offset reset
- **18-interactive-queries.md** — read from application state directly

### Platform and meta patterns
- **19-event-streaming-platform.md** — platform component model
- **20-data-contract.md** — schema-based decoupling between EPAs
- **21-event-serializer-deserializer.md** — Avro and schema registry
- **22-event-envelope.md** — Cloud Events, metadata envelopes
- **23-event-processor-and-application.md** — processor unit and composition

### Single-event processor types (stateless)
- **24-content-filter.md** — drop fields (not events)
- **25-event-filter.md** — drop events by predicate
- **26-event-translator.md** — transform attribute values/structure
- **27-event-router.md** — split to multiple outputs by condition
- **28-event-splitter.md** — explode one event into child events
- **29-event-stream-merger.md** — merge multiple streams into one

### Reliability and large events
- **30-idempotent-writer-reader.md** — exactly-once semantics in Kafka
- **31-large-events-patterns.md** — claim check and event chunking

## Primary Sources

*Kafka — The Definitive Guide* (O'Reilly); *Mastering Kafka Streams and
ksqlDB* (Seymour, O'Reilly); *Event Processing in Action* (Etzion &
Niblett, Manning); *Event Streams in Action* (Dean & Crettaz, Manning);
*Enterprise Integration Patterns* (Hohpe & Woolf);
https://developer.confluent.io/patterns
