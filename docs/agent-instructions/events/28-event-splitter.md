# Processor Type: Event Splitter

An **Event Splitter** splits original events into child events and
publishes one event per child.

## Characteristics

- One input, one output stream
- Can split events into **zero or more child events**, processed separately
- Splitting **can result in changes of the key**

```
                 Event Splitter
Input Stream ──► ░░░░░░░░░░░░░ ──► Output Stream
                 A,B,C  →  A B C
```

## Kafka Streams — `flatMapValues`

To split an event into several child events **without changing the key**,
use `flatMapValues`. Values and value types can be modified.

Advantage of `flatMapValues` over `flatMap`: it **does not cause data
re-partitioning** (key unchanged).

```java
builder
  .stream("sentences")
  .flatMapValues((k, v) ->
      Arrays.asList(v.toLowerCase().split(" ")))
  .to("words");
```

Use `flatMap` instead when the split also changes the key
(re-partitioning will occur).

## ksqlDB (SQL-style) — `EXPLODE`

Use the built-in table function `EXPLODE` to split an array-valued
attribute into multiple child events.

```sql
-- Input: {sensor_id:12345, readings:[23,56,3,76,75]}
CREATE STREAM exploded_stream AS
  SELECT sensor_id, EXPLODE(readings) AS reading
  FROM batched_readings;
-- Output: {sensor_id:12345, reading:23}, {sensor_id:12345, reading:56}, ...
```

## Related Pattern

Related to **Event Splitter** (Splitter) in *Enterprise Integration
Patterns*.

## Source

Lecture 8, HSG ICS.
