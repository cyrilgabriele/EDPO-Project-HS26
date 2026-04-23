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

Lecture 8, HSG ICS.
