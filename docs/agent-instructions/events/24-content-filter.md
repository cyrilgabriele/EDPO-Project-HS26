# Processor Type: Content Filter

A **Content Filter** reduces each event to a subset of its attributes
without dropping any events.

## Characteristics

- One input, one output stream
- **Keeps all events** (nothing is filtered out)
- **Reduces each event to a subset of attributes**
- Attribute values are **unchanged**

```
Stream ──► ░░░░░░ ──► Filtered Stream
    Field A, B, C  →  Field A, C         (B dropped)
```

## Kafka Streams

`map` / `mapValues` is used — in KStreams, content filter and event
translation are implemented with the same operators (distinction is intent).

```java
builder
  .stream("temperature", Consumed.with(Serdes.Long(), readingSerde))
  .mapValues((reading) -> {
      DeviceTemperature devTemp = new DeviceTemperature();
      devTemp.setDeviceID(reading.getDeviceID());
      devTemp.setTemperature(reading.getTemperature());
      return devTemp;
  })
  .to("deviceTemp", Consumed.with(Serdes.Long(), deviceTempSerde));
```

## ksqlDB (SQL-style)

Use `SELECT` to project a subset of columns. Output partitioning follows
the input by default; `PARTITION BY` can repartition.

```sql
CREATE STREAM temperature (deviceID BIGINT KEY, roomNo INT, temperature DOUBLE)
  WITH (kafka_topic='temperature', partitions=1, value_format='json');

CREATE STREAM deviceTemp AS
  SELECT deviceID, temperature FROM temperature;
```

## Related Pattern

Related to **Content Filter** in *Enterprise Integration Patterns* (Hohpe
& Woolf).

## Source

Lecture 8, HSG ICS.
