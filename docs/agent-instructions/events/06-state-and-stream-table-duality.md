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

## Source

Lecture 7, HSG ICS.
