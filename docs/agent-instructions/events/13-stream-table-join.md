# Pattern: Stream–Table Join (External Lookup)

Stream processing sometimes requires integration with data **external to the
stream** — enriching each event with reference data (e.g. user profile).

## Naïve Approach — External Lookup per Event

```
Click topic ──► Enrich ──► Enriched click topic
                  │
                  ▼
           Profile Database
```

Problems with hitting the external store per record:
- Adds **significant latency** per record (5–15 ms)
- Extra **load on the external datastore** may be unacceptable
- **Availability** of the external store becomes a dependency for the
  stream pipeline

## Cached Approach — Stream–Table Join

To get good performance and availability, the information from the database
is **cached in the stream processing application**.

Idea: capture **changes to the database table as a stream of events**
(change data capture / CDC). The local cache is updated upon changes.

```
Click topic   ─────┐
                   ▼
Profile topic ──► Processor ──► Enriched click topic
   ▲              (Join)
   │              Local state:
Profile             cached copy
database            of profiles
```

This is a form of **ECST** (Event-Carried State Transfer); it reduces
latency and eliminates runtime dependency on the database.

## Join Operators

`join` (inner) and `leftJoin` are available for stream–table joins.
`outerJoin` is **not** supported here (only for stream–stream and
table–table joins).

| Operator    | Semantics                                            |
|-------------|------------------------------------------------------|
| `join`      | Inner — emit only when the left side has a match     |
| `leftJoin`  | Left — always emit on left; right may be `null`      |

## Co-partitioning Requirement (KStream × KTable)

Both sides must agree on:

1. **Same keys** (logical key with matching serialization)
2. **Same partitioning strategy**
3. **Same number of partitions**

If keys do not align, use `selectKey(...)` on the stream — this triggers
a repartition (see `12-multiphase-repartitioning.md`).

## KStream × KGlobalTable Variant

When the reference table is small and static, model it as a
**GlobalKTable** instead. This:

- **Removes the co-partitioning requirement** — every task has the full
  table.
- Allows the join key to be derived from a *value field* via a
  `KeyValueMapper<K, V, KO>` rather than being identical to the stream key.
- Cannot use `outerJoin` (only `join` and `leftJoin`).

```java
KStream<String, Enriched> withProducts =
    withPlayers.join(
        products,                                              // GlobalKTable
        (leftKey, scoreWithPlayer) ->                          // KeyValueMapper
            String.valueOf(scoreWithPlayer.scoreEvent().productId()),
        (scoreWithPlayer, product) -> new Enriched(scoreWithPlayer, product));
```

## Value Joiner

A `ValueJoiner` defines how the two sides combine into the output value
(equivalent to SQL's `SELECT` list). Two common shapes:

- **Wrapper class** — keep both records intact:
  `(score, player) -> new ScoreWithPlayer(score, player)`
- **Property extraction** — flatten into a new value class with chosen
  fields only.

## Benefits

- ✅ Microsecond-level lookups from local state (no remote call)
- ✅ Pipeline stays available if the source DB is briefly down
- ✅ No extra read load on the operational DB

## Trade-offs

- ❌ Local cache may lag behind the source DB (eventual consistency)
- ❌ Memory cost grows with table size
- ❌ Requires a reliable CDC pipeline from DB → profile topic

## Source

Lectures 8 & 9, HSG ICS.
