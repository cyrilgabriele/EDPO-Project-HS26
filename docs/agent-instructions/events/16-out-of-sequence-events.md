# Pattern: Out-of-Sequence Events

In real systems, events may arrive out of order relative to their event time
— network delays, upstream batching, retries, or clock skew. Correct stream
processing must handle this.

## Core Principles

- Events **may arrive late**
- Processing must **handle reordering**
- A **grace period** allows late arrivals to still update the correct window

## Example

Event-time stream with late arrivals:

```
5:01  5:02  5:03  [4:45  4:46  4:47]  5:04  5:05  5:06  5:07
                   └── older events arriving late ──┘
```

The 4:45–4:47 events arrive *after* the 5:03 event in wall-clock order but
belong to earlier windows. A grace period keeps the earlier windows
updatable long enough to absorb these late events.

## Mechanism

> Detect out-of-order events and reconcile them **during a grace period**.

For each time window:
1. Window closes at its event-time end
2. Window remains **open for updates** for the grace period
3. Events arriving during the grace period still contribute to that window
4. After the grace period, the window is sealed and late events are
   dropped (or sent to a dead-letter stream)

## Configuration Considerations

- **Too short a grace period** → late events dropped, results inaccurate
- **Too long a grace period** → results take longer to finalize, more
  state retained
- Choose based on measured end-to-end latency and business tolerance

## Relation to Time Semantics

This pattern only makes sense with **event time** (not processing time) —
by definition, there is no "late" in processing time.

## Source

Lecture 8, HSG ICS.
