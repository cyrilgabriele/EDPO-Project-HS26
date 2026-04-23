# Processor Type: Event Translator

An **Event Translator** transforms attribute values and/or event structure
while preserving the 1:1 mapping between input and output events.

## Characteristics

- One input, one output stream
- **Modifies attribute values**
- **May change event structure**
- **No events added or removed** (1 in → 1 out)

```
Stream ──►  ░░░░░░  ──► Stream
            ┌────────────────────────┐
            │ Original    Translated │
            │ Field A ──► Field X    │
            │ Field B ──► Field Y    │
            │ Field C ──► Field Z    │
            └────────────────────────┘
```

## Kafka Streams

Use `mapValues` to set/modify attribute values and types.

```java
builder
  .stream("input-topic")
  .mapValues((v) -> v.toUpperCase())
  .to("output-Topic");
```

Note: in KStreams, the implementation of both **content filter** and
**event translation** uses `map` / `mapValues`. The distinction is in
*intent*: content filter drops fields; translation changes values/types.

## ksqlDB (SQL-style)

Mathematical and logical operators as well as functions can be used to
derive new attribute values.

```sql
CREATE STREAM temperature (deviceID BIGINT KEY, roomNo INT, temperature DOUBLE)
  WITH (kafka_topic='temperature', partitions=1, value_format='json');

CREATE STREAM outputStream AS
  SELECT deviceID, roomNo, temperature * 9/5 + 32 AS temp
  FROM temperature;
-- translates temperature from Celsius to Fahrenheit
```

## Related Pattern

Related to **Message Translation** in *Enterprise Integration Patterns*
(Hohpe & Woolf).

## Source

Lecture 8, HSG ICS.
