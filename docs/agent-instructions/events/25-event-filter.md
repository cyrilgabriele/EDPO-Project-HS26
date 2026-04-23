# Processor Type: Event Filter

An **Event Filter** drops events that do not match a predicate, forwarding
the rest unchanged. Distinct from the **Content Filter** (which drops
attributes, not events).

## Characteristics

- One input, one output stream
- Events fulfilling the predicate are **forwarded** (or **dropped**)
- **No transformation** capabilities
- Does **not change** or **duplicate** events

```
Input Stream ──► ░░░░░░ ▼ ░░░░░░ ──► Output Stream
                 (event.temperature > 20.5)
```

## Kafka Streams

- `filter(predicate)` — **forwards** events where predicate is `true`
- `filterNot(predicate)` — **drops** events where predicate is `true`

```java
builder
  .stream("temperature")
  .filter((key, reading) -> reading.temperature >= 20.5)
  .to("high_temperature");
```

## ksqlDB (SQL-style)

Use `SELECT … WHERE` to express the predicate.

```sql
CREATE STREAM high_temp WITH (kafka_topic='high_temperature') AS
  SELECT *
  FROM temperature
  WHERE temperature >= 20.5;
```

## Content Filter vs. Event Filter

| Aspect         | Content Filter        | Event Filter       |
| -------------- | --------------------- | ------------------ |
| Drops events?  | No                    | Yes                |
| Drops fields?  | Yes                   | No                 |
| Transforms?    | No (values unchanged) | No                 |

## Related Pattern

Related to **Message Filter** in *Enterprise Integration Patterns*.
Tutorial: https://developer.confluent.io/tutorials/filter-a-stream-of-events/ksql.html

## Source

Lecture 8, HSG ICS.
