# Pattern: Data Contract

When an Event Processing Application (EPA) sends an event to another EPA,
it is **crucial that the receiver understands how to process the shared
event**. A data contract makes this explicit.

## Definition

Using a **data contract** (a schema), EPAs can share events and understand
how to process them — **without the sending and receiving application
knowing any details about each other**.

The contract decouples producer from consumer; both sides code against the
schema rather than against each other.

## Structure

```
┌──────────────────┐         Stream          ┌──────────────────┐
│ Event Processing ├───────► ░░░░░░░ ───────►│ Event Processing │
│ Application      │                         │ Application      │
└────────┬─────────┘                         └────────┬─────────┘
         │                                            │
         └──────────────►  Schema  ◄──────────────────┘
                            ✓
```

Both producer and consumer reference the same schema, which lives
independently of them.

## Why This Matters

- **Evolution** — schema evolution rules (backward/forward compatibility)
  let producers and consumers upgrade independently
- **Discoverability** — new consumers can inspect the schema to know what
  an event contains
- **Validation** — events can be validated against the schema at produce
  and consume time

## Concrete Realizations

- **Avro** schemas (most common in Kafka ecosystems)
- **Protobuf** schemas
- **JSON Schema**
- Optionally managed by a **Schema Registry** (Confluent Schema Registry,
  Apicurio)

## Related Patterns

Data contract is the foundation for:
- **Event Serializer** (write to binary using schema)
- **Event Deserializer** (reconstruct event using schema)
- **Event Envelope** (wraps payload with common metadata)

## Source

Lecture 8, HSG ICS.
