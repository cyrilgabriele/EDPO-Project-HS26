# State and Stream–Table Duality

## Stateless vs. Stateful Event Processing

### Stateless
- Each event processed **independently**
- **No memory** of previous events
- Examples: filtering, routing, transformation

### Stateful
- Processing **depends on multiple events**
- Requires **maintaining state** between events
- Examples: counting, joins, moving averages

Stateless operations are cheap and easy to parallelize. Stateful operations
require care around partitioning, persistence, and recovery.

## Stream–Table Duality

Core insight:

- **Stream** = sequence of changes (an event log)
- **Table** = current state derived from applying those changes
- **Materializing a stream** = converting a stream into a table

Any table can be represented as the stream of changes that produced it, and
any stream can be folded into a table representing its current state.

## Projection Table

A **projection table** is the materialized view of a stream of events:

- Whenever a new event arrives, the table is automatically updated
- The only way to change it is to record new events on the source stream

The duality made concrete: the table side of a stream is its projection.
"Latest price per symbol" or "current holdings per user" KTables are
projection tables built from `crypto.price.clean` and
`transaction.order.approved` respectively.

## Shoe Inventory Example

Stream of inventory changes:
1. "Shipment arrived with red, blue, and green shoes." — Shipment
2. "Blue shoes sold." — Sale
3. "Red shoes sold." — Sale
4. "Blue shoes returned." — Return
5. "Green shoes sold." — Sale

Event log entries (key, value):
```
(blue,  300)  shipment
(red,   300)  shipment
(green, 300)  shipment
(blue,  299)  sale
(red,   299)  sale
(blue,  300)  return
(green, 299)  sale
```

Materialized table (latest state per key):
```
red shoes   → 299
blue shoes  → 300
green shoes → 299
```

## KTable vs. GlobalKTable

Two abstractions for representing a table in Kafka Streams.

| Property        | KTable                       | GlobalKTable                       |
|-----------------|------------------------------|------------------------------------|
| Distribution    | Partitioned across tasks     | Fully replicated to every instance |
| Scales with     | Data size                    | Constant overhead per instance     |
| Suited for      | Large, evolving datasets     | Small, static reference data       |
| Co-partitioning | Required for joins           | **Not required** (full replica)    |
| ksqlDB support  | Yes                          | No                                 |

### Choosing the source abstraction

- **KStream** — unkeyed events or events where every occurrence matters
  (clicks, price ticks). Round-robin partitioned, append-only.
- **KTable** — keyed compacted topic, large or evolving keyspace, latest
  value per key (user holdings, order status, latest prices).
- **GlobalKTable** — keyed compacted topic, *small and static* keyspace,
  every task needs the full lookup view (FX rates, country reference data).

> GlobalKTable has **no direct operations** of its own — it only appears
> as the right side of a stream–table join.

## Source

Lectures 7 & 9, HSG ICS.
