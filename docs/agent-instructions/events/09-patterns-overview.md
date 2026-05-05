# Event Processing Design Patterns — Overview

Catalog of stream processing design patterns covered in **Lectures 8–9**,
grouped by the course project's requirement tiers.

## Minimal Requirements (covered in Week 8)

Required baseline patterns; the project must use most of these:

1. **Single-Event Processing** — map/filter per event
2. **Processing with Local State** — stateful aggregation per partition
3. **Multiphase Processing / Repartitioning** — local-then-global aggregation
4. **Processing with External Lookup (Stream–Table Join)** — enrichment from external data

## Additional Patterns (at least one, more is better)

Covered in weeks 9–10:

5. **Table–Table Join** — join two materialized tables
6. **Streaming Join** — windowed join between two streams
7. **Out-of-Sequence Events** — handling late-arriving events with grace period
8. **Reprocessing** — rerun logic on existing event stream
9. **Interactive Queries** — read results directly from application state

## Project Requirements

1. Develop one or several stream processing applications
2. Applications should include **both stateless and stateful** processing
   - Several stateless operations (Week 8)
   - Use of both streams and tables (Week 9)
   - Consider data from more than one stream (Week 9)
   - Interactive queries (Week 9)
   - Windowed operations (Week 10)
3. Describe each application's **topology in graphical form**
4. Elaborate on the chosen patterns
5. Use **Avro schema** (registryless or with a schema registry)

## Source

Lectures 8 & 9, HSG ICS. Patterns based on *Kafka — The Definitive Guide* and
https://developer.confluent.io/patterns
