# Pattern: Single-Event Processing

Most basic pattern of stream processing — the processing of **each event in
isolation**. Also known as the **map/filter** pattern.

## Characteristics

- **Stateless** — no memory between events
- Trivially parallelizable
- Easy recovery from application failures (no state to restore)
- Easy load balancing (any consumer can handle any event)
- Can be handled with a **simple producer and consumer** — no stream
  processing framework strictly required

## Example Topology

A log router that routes error events to a high-priority topic and passes
other events through to Avro logs:

```
Topic ──log events──► Branch ──error events──► High priority topic
                        │
                        └──other events──► Low priority topic
                                                │
                                                ▼
                                          Convert to Avro ──► Avro logs
```

## Typical Operators

Operations that fit this pattern:
- Filter (keep or drop events)
- Map / translate (transform a single event)
- Route / branch (send events to different sinks based on content)

## Trade-offs

- ✅ Simple, stateless, easily recoverable
- ✅ Scales horizontally without coordination
- ❌ Cannot compute anything requiring multiple events (aggregates, joins)
- ❌ Enrichment against external data needs a different pattern
  (stream–table join)

## Source

Lecture 8, HSG ICS.
