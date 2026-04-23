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

## Benefits

- ✅ Microsecond-level lookups from local state (no remote call)
- ✅ Pipeline stays available if the source DB is briefly down
- ✅ No extra read load on the operational DB

## Trade-offs

- ❌ Local cache may lag behind the source DB (eventual consistency)
- ❌ Memory cost grows with table size
- ❌ Requires a reliable CDC pipeline from DB → profile topic

## Source

Lecture 8, HSG ICS.
