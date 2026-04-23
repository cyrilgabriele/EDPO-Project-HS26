# Processor Type: Event Stream Merger

An **Event Stream Merger** merges several event streams into a single
stream. Typically a stateless stream processor.

## Characteristics

- **Multiple input streams, one output stream**
- Events are **forwarded unchanged**
- **No ordering guarantees across streams**

```
Stream A ─┐
          ├──► ░░░░░░ ──► Merged Stream
Stream B ─┘
```

## Kafka Streams — `merge`

```java
KStream<byte[], Tweet> mergedStream = englishStream.merge(translatedStream);
```

Constraints:
- Kafka does **not** guarantee processing order across the underlying streams
- Streams can only be merged if their events have the **same key and
  value types**

## ksqlDB (SQL-style) — `INSERT INTO`

Create a target stream and insert from each source into it.

```sql
CREATE STREAM rock_songs (artist VARCHAR, title VARCHAR)
  WITH (kafka_topic='rock_songs', partitions=1, value_format='avro');

CREATE STREAM classical_songs (artist VARCHAR, title VARCHAR)
  WITH (kafka_topic='classical_songs', partitions=1, value_format='avro');

CREATE STREAM all_songs (artist VARCHAR, title VARCHAR, genre VARCHAR)
  WITH (kafka_topic='all_songs', partitions=1, value_format='avro');

INSERT INTO all_songs
  SELECT artist, title, 'rock' AS genre FROM rock_songs;

INSERT INTO all_songs
  SELECT artist, title, 'classical' AS genre FROM classical_songs;
```

## Use Cases

- Recombining branches after routing (e.g. translate-then-merge in the
  CryptoSentiment topology)
- Unifying events from parallel pipelines into one downstream consumer

## Source

Lecture 8, HSG ICS.
