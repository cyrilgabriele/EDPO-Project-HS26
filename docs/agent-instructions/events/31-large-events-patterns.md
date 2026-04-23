# Patterns for Handling Large Events

Two patterns for when events exceed sensible size limits of the event
streaming platform.

## Pattern 1: Claim Check

When events are too large to move through the platform, the event
**payload is stored externally** and only a **pointer (the "claim check")
is sent** through the stream.

```
           Stream
         ░░░░░░░░░
             │
             ▼
  s3://…/payloads/fadd349b42     ◄── pointer only
             │
             ▼
     External Store (S3)
     [large payload]
```

Trade-offs:
- ✅ Keeps the event log small and fast
- ✅ Payload can be very large (GBs)
- ❌ Extra system to operate; store availability affects consumers
- ❌ Lifecycle management (retention, deletion) more complex

Related: **Claim Check** in *Enterprise Integration Patterns*.

## Pattern 2: Event Chunking

Instead of storing the event whole, split it into smaller events
(**"chunking"**), send the chunks, and **re-assemble on the client side**.

```
 Event Source                                   Event Processor
┌───────────┐   Stream                         ┌───────────┐
│  [Event]  │  ░░░░░░░                         │  [Event]  │
│     ↓     │►[4/4][3/4][2/4][1/4]───────────► │     ↑     │
│  chunks   │                                  │  chunks   │
└───────────┘                                  └───────────┘
```

Trade-offs:
- ✅ No external storage required
- ✅ Payload stays inside the streaming platform
- ❌ Reassembly logic needed at every consumer
- ❌ Chunk ordering and loss handling add complexity

## Choosing Between Them

- **Claim Check** — very large or binary payloads, object store exists
- **Chunking** — stay within the streaming platform, moderately oversized

## Source

Lecture 8, HSG ICS. Further reading:
https://dzone.com/articles/processing-large-messages-with-apache-kafka
