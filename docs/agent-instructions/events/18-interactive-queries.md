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

Lecture 8, HSG ICS.
