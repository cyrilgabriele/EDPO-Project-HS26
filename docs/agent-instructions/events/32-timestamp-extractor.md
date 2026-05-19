# Timestamp Extractor

A `TimestampExtractor` is the Kafka Streams component that **associates a
record with a timestamp**. Time semantics (event time vs ingestion vs
wall-clock) are controlled here — not by broker config alone.

## Interface

```java
public interface TimestampExtractor {
    long extract(ConsumerRecord<Object, Object> record,
                 long partitionTime);
}
```

- `record` — the consumed Kafka record (key, value, headers, metadata).
- `partitionTime` — the **highest timestamp Kafka Streams has seen on
  this partition so far**, passed in as a sane fallback when no
  timestamp can be extracted from the record itself.

## Built-in extractors

| Extractor | Yields |
|---|---|
| `FailOnInvalidTimestamp` (default) | record metadata timestamp — event time *or* ingestion time depending on producer/topic config. Fails fast on invalid `(< 0)` timestamps. |
| `LogAndSkipOnInvalidTimestamp` | same source, but logs and skips invalid records instead of failing |
| `WallclockTimestampExtractor` | the broker/JVM wall-clock when the record is processed → **processing-time semantics** |

> Choose based on the time semantic you want, then back it up with the
> appropriate broker/topic `message.timestamp.type` setting (see
> `05-time-semantics.md`).

## Custom extractor — timestamp from payload

The most common reason to write a custom extractor: the **event-time
timestamp is embedded in the payload** (e.g. an ISO-8601 string or epoch
field on the value), not on the record metadata.

```java
public class VitalTimestampExtractor implements TimestampExtractor {

    @Override
    public long extract(ConsumerRecord<Object, Object> record,
                        long partitionTime) {
        Vital measurement = (Vital) record.value();
        if (measurement != null && measurement.getTimestamp() != null) {
            return Instant.parse(measurement.getTimestamp())
                          .toEpochMilli();
        }
        return partitionTime;        // fallback strategy
    }
}
```

## Strategies when no timestamp can be extracted

Three accepted approaches:

1. **Throw an exception** — stop processing; useful when missing
   timestamps indicate corrupt data.
2. **Fall back to `partitionTime`** — keep moving but pin this record to
   the latest timestamp already seen on the partition.
3. **Return a negative timestamp** — Kafka Streams treats this as
   "drop"; the record is skipped.

Pick (1) for hard correctness, (2) for graceful degradation, (3) when
the upstream is known to occasionally emit timestampless records that
should be silently ignored.

## Registering an extractor

Bind the extractor at the source via `Consumed.withTimestampExtractor`:

```java
StreamsBuilder builder = new StreamsBuilder();

Consumed<String, Pulse> pulseConsumerOptions =
    Consumed.with(Serdes.String(), JsonSerdes.Pulse())
            .withTimestampExtractor(new VitalTimestampExtractor());

KStream<String, Pulse> pulseEvents =
    builder.stream("pulse-events", pulseConsumerOptions);
```

A different extractor can be registered per source stream, so streams
with different time conventions can co-exist in the same topology.

## When to write one

Write a custom extractor whenever:

- The event-time timestamp lives in the **payload** (typical with JSON
  events).
- You need **deterministic event-time** independent of broker /
  ingestion clock skew.
- You want the topology to behave the same on **reprocessing** as on
  live data — the only way is to drive timestamps from the payload.

Use a built-in extractor when the producer already stamps records with
`CreateTime` set to the true event time, or when wall-clock semantics
are intentional.

## Source

Lecture 10, HSG ICS. See also Mitch Seymour, *Mastering Kafka Streams
and ksqlDB*, ch. 5.
