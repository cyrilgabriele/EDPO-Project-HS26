# Processor Type: Event Router

An **Event Router** splits the input stream into several output streams
based on conditions, without modifying events.

## Characteristics

- **One input, multiple output streams**
- **Routes events based on conditions**
- **Does not modify** events
- **Does not duplicate** events (each event goes to one output)

```
                Event Router
Input Stream ──► ░░░░░░░░░░░ ──► Stream A
                             ──► Stream B
                             ──► Stream C
```

## Kafka Streams — `branch` (deprecated) / `split`

Branching decisions are based on supplied **Predicates**, fixed in number.
Output topics must be created ahead of running the application.

```java
StreamsBuilder builder = new StreamsBuilder();
KStream<Long, Reading> readings = builder.stream("temperature");

final KStream<Long, Reading>[] branches = readings.branch(
    (id, reading) -> reading.getTemperature() >= 20.5,
    (id, reading) -> reading.getTemperature() < 19);

branches[0].to("high_temperature");
branches[1].to("low_temperature");
```

Note: `branch` is **deprecated** and replaced by `split`. In the course,
Lab12P1 uses `split`, Lab12P2 uses `branch`.

## ksqlDB (SQL-style)

Create several `CREATE STREAM AS SELECT` statements, each with a different
`WHERE` clause.

```sql
CREATE STREAM high_temp WITH (kafka_topic='high_temperature') AS
  SELECT * FROM temperature WHERE temperature >= 20.5;

CREATE STREAM low_temp WITH (kafka_topic='low_temperature') AS
  SELECT * FROM temperature WHERE temperature < 19;
```

## Related Pattern

Related to **Message Router** in *Enterprise Integration Patterns*.
Blog: https://www.confluent.io/blog/putting-events-in-their-place-with-dynamic-routing/

## Source

Lecture 8, HSG ICS.
