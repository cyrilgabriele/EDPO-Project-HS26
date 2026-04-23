# Processing Guarantees

Stream processing systems can offer different delivery/processing guarantees.
Choice depends on correctness requirements vs. performance cost.

## The Three Guarantees

### At-most-once
- Each event is processed zero or one time
- **Loss** is possible; **duplicates** are not
- Lowest overhead; suitable when occasional loss is acceptable
  (e.g. non-critical metrics)

### At-least-once
- Each event is processed one or more times
- **No loss**; **duplicates** are possible
- Retries on failure lead to possible reprocessing of the same event
- Downstream must be idempotent or tolerate duplicates

### Exactly-once
- Each event is processed exactly one time from the stream processor's
  perspective
- Strongest guarantee; highest coordination cost
- Ensures accurate results (state updates happen once)

## Important Caveat

Exactly-once ensures accurate **in-system** results, but **side-effects may
still occur**. If a processor sends an email, charges a card, or calls an
external API, the stream processor can guarantee internal state consistency
but not that the external side effect happened exactly once — external
systems must cooperate (idempotency keys, transactional outbox, etc.).

## Kafka Configuration (relevant keys)

- `enable.idempotence=true` — idempotent producer (deduplicates producer retries)
- `isolation.level=read_committed` — consumer only reads committed records
- `processing.guarantee=exactly_once` — Kafka Streams exactly-once semantics

## Source

Lecture 7, HSG ICS. See also Confluent:
https://www.confluent.io/blog/simplified-robust-exactly-one-semantics-in-kafka-2-5/
