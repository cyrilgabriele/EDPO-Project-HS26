# Pattern: Multiphase Processing / Repartitioning

Used when computation requires **global information** that cannot be produced
from a single partition's local state alone.

## Three Steps

1. **First aggregate locally** — compute partial results per partition
2. **Repartition data** — shuffle partial results into a partition layout
   that supports the global computation (often a single partition)
3. **Perform a second aggregation** — combine the partial results into the
   final global result

## Canonical Example — Top 10

To find the top 10 stock gainers across all symbols:

- Local processing computes daily gain/loss per symbol per partition
- Aggregated processing must happen **in one partition** so a single
  processor can see all symbols and pick the top 10

## Topology

```
Trades Topic    Processors (local)       Gain/lose     Processor        Top 10
┌────────────┐  ┌───────────────────┐   ┌───────────┐  ┌─────────────┐  ┌─────────┐
│ A–N part.  ├─►│ Local state       ├──►│ 1 part.   ├─►│ Local state ├─►│ 1 part. │
│            │  │ Daily gain/lose   │   └───────────┘  │ Top 10      │  └─────────┘
│ O–Z part.  ├─►│ Local state       ├──►                └─────────────┘
└────────────┘  │ Daily gain/lose   │
                └───────────────────┘
```

Key detail: the intermediate "gain/lose topic" has **1 partition** so the
second aggregation sees all keys in one place.

## Trade-offs

- ✅ Enables global aggregates while keeping the first phase parallel
- ❌ Repartition step adds network traffic and latency
- ❌ A single downstream partition is a scalability bottleneck — use only
  when the second aggregation's output is small (top-N, totals)

## Source

Lecture 8, HSG ICS.
