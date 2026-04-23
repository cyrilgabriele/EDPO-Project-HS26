# Time Windows

Most stateful operations use time windows: moving averages, joins,
aggregations. Having a common window model is critical.

## Key Parameters

- **Size** — the duration the window spans
- **Advance / hop** — how often the window moves
- **Grace period** — how long the window remains updatable for late arrivals

## Three Types of Windows

### Tumbling Window (time-based)
- Fixed-duration, **non-overlapping**, **gap-less** windows
- Each event belongs to exactly one window
- Use case: per-minute aggregates, hourly counters

```
|--size--|--size--|--size--|
[ win n ][ win n+1 ][ win n+2 ]   events →
```

### Hopping Window (time-based)
- Fixed-duration, **overlapping** windows
- Defined by size and advance-by (hop); overlap = size − advance
- Each event may belong to multiple windows
- Use case: sliding moving averages

```
[ win n       ]
   [ win n+1       ]
      [ win n+2       ]
advance by | ── duration ──|
```

### Session Window (session-based)
- **Dynamically-sized**, non-overlapping, **data-driven**
- Window closes after an **inactivity gap** Δt exceeding a threshold
- Use case: user session aggregation where session length varies

```
[ session n ]    Δt > inactivity gap    [ session n+1 ]
```

## Source

Lecture 7, HSG ICS. Window definitions from ksqlDB docs:
https://docs.ksqldb.io/en/latest/concepts/time-and-windows-in-ksqldb-queries/
