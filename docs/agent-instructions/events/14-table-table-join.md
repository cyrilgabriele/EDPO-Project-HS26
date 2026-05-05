# Pattern: Table–Table Join

Join between **two materialized tables** rather than streams.

## Characteristics

- Join two materialized tables (not event streams)
- **Non-windowed** operation — no time bounds
- Uses the **latest state** from both tables
- Emits updates whenever either side changes

## Types

### `equi-join` (same key)
Both tables are keyed on the same attribute; join on key equality.
Standard left/inner/outer joins over the current table state.

### `foreign-key join` (lookup by different key)
One table references the other by a non-key attribute. Kafka Streams
supports foreign-key table joins where the join key differs between sides.

## When to Use

- Materializing enriched reference data from two evolving sources
- Building derived tables that combine state (e.g. orders × customers with
  full current customer info)
- Anywhere you need the *current* combined state rather than a
  windowed event intersection

## Operators

`join`, `leftJoin`, and `outerJoin` are all available for table–table
joins; semantics match SQL. The output table emits a change whenever
either side changes.

## Co-partitioning

Equi-joins (same key on both sides) require co-partitioning: matching
key serdes, partitioning strategy, and partition count.

**Foreign-key joins** lift this requirement — Kafka Streams handles the
necessary internal repartitioning so that the right side can be looked
up by a foreign key extracted from the left value. The FK extractor has
the signature `Function<V, KO>` (or a `BiFunction<K, V, KO>` overload),
so the value carries the foreign key.

## Contrast with Streaming Join

| Aspect        | Table–Table       | Streaming (Stream–Stream)   |
| ------------- | ----------------- | --------------------------- |
| Operands      | Two tables        | Two streams                 |
| Windowed      | No                | Yes                         |
| Uses          | Latest state      | Events within time window   |
| Output        | Emitted on change | Emitted on matching join    |

## Further Reading

Confluent on Kafka Streams foreign-key joins:
https://www.confluent.io/blog/data-enrichment-with-kafka-streams-foreign-key-joins/

## Source

Lectures 8 & 9, HSG ICS.
