# Patterns: Event Serializer / Deserializer

Complementary patterns bridging in-memory event objects and the binary form
transported through the event streaming platform.

## Event Serializer (producing side)

Converts events to binary form before publishing.

- **Convert events to binary format**
- **Use a schema-based format** (e.g. Avro)
- **Optional**: use of a schema registry

```
Application
┌──────────────────────────────────────┐
│ Logic ──► Events ──► Serializer ──► ─┤──► Stream
└──────────────────────────────────────┘
                │
                ▼ (optional)
           Schema Registry
```

## Event Deserializer (consuming side)

Reconstructs the original event from the event streaming platform.

- To make deserialization easy, **both the event data and its schema
  should be readily available**
- An Avro library can use the schema to deserialize events
- **Optional**: use of a schema registry

```
           Application
           ┌──────────────────────────────────────────────┐
Stream ──► │ Serialized Events ──► Deserializer ──► Events│ ──► Logic
           └──────────────────────────────────────────────┘
                            │
                            ▼ (optional)
                       Schema Registry
```

## Why Binary + Schema?

- **Compact on-wire format** (Avro/Protobuf vs JSON) — lower cost
- **Strong typing** — errors caught at serialization time
- **Schema evolution** — writers and readers can use different versions

## With vs. Without Schema Registry

- **With registry**: schema stored centrally, referenced by ID in the
  record; supports governance
- **Registryless**: schema embedded with each record or assumed known;
  simpler, less governance

## Source

Lecture 8, HSG ICS. Further reading:
https://developer.confluent.io/patterns/event/event-deserializer
