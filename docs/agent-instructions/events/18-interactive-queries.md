# Pattern: Interactive Queries

Interactive queries expose a stream processor's **internal state** directly
to external readers, instead of requiring them to consume output topics.

## Core Idea

- **Read results from application state** (the processor's local state
  store)
- **Avoid consuming output topics** to get current values
- **Enables low-latency lookups** — point queries against in-memory /
  RocksDB state

## Typical Shape

A stream processing app maintains a materialized aggregate (e.g. current
balance per account, top-N, per-user counters). A query API is exposed so
that:

```
client → HTTP/gRPC → stream app instance → local state store → response
```

If the requested key lives on another instance, the query is forwarded
to that instance (metadata discovery tells the app which instance owns
which partition).

## Materialized vs. Internal State Stores

Stateful operators (`aggregate`, `reduce`, `count`) always use a state
store. Two visibility modes:

- **Internal store** — anonymous, only accessed inside the topology.
- **Materialized store** — explicitly named via `Materialized.as("name")`,
  *queryable from outside* the topology. Naming the store is what makes
  it eligible for interactive queries.

```java
KTable<String, HighScores> highScores = grouped.aggregate(
    initializer, adder,
    Materialized.<String, HighScores, KeyValueStore<Bytes, byte[]>>
        as("leader-boards")
        .withKeySerde(Serdes.String())
        .withValueSerde(JsonSerdes.HighScores()));
```

## Read-Only Access

Get a read-only handle to the store using its name and type:

```java
ReadOnlyKeyValueStore<String, HighScores> store =
    streams.store(StoreQueryParameters.fromNameAndType(
        "leader-boards", QueryableStoreTypes.keyValueStore()));
```

For non-windowed key–value stores the API is:

| Method                                          | Use                              |
|-------------------------------------------------|----------------------------------|
| `V get(K key)`                                  | Point lookup                     |
| `KeyValueIterator<K,V> range(K from, K to)`     | Inclusive range scan             |
| `KeyValueIterator<K,V> all()`                   | All entries                      |
| `long approximateNumEntries()`                  | Rough size                       |

> **Always close iterators** (`try-with-resources`) — leaking them
> leaks RocksDB resources.

For exact counts, use an in-memory store; persistent stores only return
approximations.

## Local vs. Remote Queries

- **Local** — each instance can serve queries against the partitions it
  owns. Fast, but a *partial* view of the application state.
- **Remote** — to serve queries for a key on another instance, locate the
  right instance via Kafka Streams metadata and forward via RPC/REST.
  Complete view, higher latency.

A typical IQ-backed API does the metadata lookup on every request and
forwards if the key isn't local.

## When to Use

- Dashboards needing current aggregate values
- Customer-facing APIs that show "current" state derived from streams
- Replacing a separate serving database for read-mostly derived data

## Trade-offs vs. External Serving DB

- ✅ One fewer system to operate — the stream processor *is* the serving
  layer
- ✅ Always up to date with the stream (no export delay)
- ❌ Query API scaling tied to stream processing cluster
- ❌ Complex cross-partition queries (joins, secondary-index lookups)
  are limited

## Relation to Stream–Table Duality

Interactive queries expose the **table side** of the duality. The stream
of updates materializes into a table; interactive queries read that table.

## Source

Lectures 8 & 9, HSG ICS.
