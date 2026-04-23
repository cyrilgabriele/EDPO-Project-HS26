# Pattern: Event Envelope

An event envelope provides a **standard set of fields across all events**
sent through event processing applications. The envelope is independent of
the underlying event payload format — analogous to **protocol headers in
networking**.

## Structure

```
Event Processing App A
        │
        ▼
      Data (payload)
        │
        ▼
     Wrapper  ──► Envelope Event    Stream    Envelope Event ──► Unwrapper
                  ┌────────────┐   ░░░░░░░   ┌────────────┐          │
                  │ Metadata    │             │ Metadata    │          ▼
                  │ Data        │             │ Data        │        Data
                  └────────────┘             └────────────┘          │
                                                                      ▼
                                                               Event Processing App B
```

Producer wraps the payload; consumer unwraps. Metadata fields remain
uniform across event types.

## Typical Envelope Fields

- `id` — unique event identifier
- `source` — origin of the event
- `type` — event type / schema reference
- `time` — timestamp
- `schema` — schema identifier or URL
- `key` — routing/partitioning key
- `dataContentType` — format of the payload (JSON, Avro, …)

## Industry Standard: Cloud Events

**Cloud Events** is a CNCF specification that standardizes access to ID,
schema, key, and other common event attributes — a widely-adopted concrete
realization of the envelope pattern.

## Benefits

- ✅ Uniform processing for cross-cutting concerns (tracing, auditing,
  routing, deduplication)
- ✅ Payload evolves independently of metadata
- ✅ Interop across different event types and producers

## Related Patterns

Related to **Envelope Wrapper** in *Enterprise Integration Patterns* by
Hohpe & Woolf.

## Source

Lecture 8, HSG ICS. See also: choosing between strict and dynamic schemas
(referenced on slide).
