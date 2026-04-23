# Pattern: Reprocessing

**Reprocessing** = rerunning a stream processing application over an
existing event stream.

## Why Reprocess?

- **Improved logic** — a better algorithm or model became available
- **Bug fixes** — original logic was wrong and results must be corrected

Reprocessing is possible because event streams are **replayable** —
this is one of the key reasons replayability is a desirable property of
event streams.

## Two Options

### Option A: Run new version in parallel
- Deploy the new version as a separate consumer group from offset 0
- Old and new versions produce outputs simultaneously
- Once the new version has caught up and is validated, cut over consumers
  to its output topic
- Old version can be decommissioned

### Option B: Reset offsets and recompute
- Stop the existing application
- Reset its consumer group offsets to the beginning (or desired point)
- Restart — the application reprocesses from the chosen offset

## Trade-offs

| Approach             | Pros                                 | Cons                                 |
| -------------------- | ------------------------------------ | ------------------------------------ |
| Parallel new version | Zero downtime; easy rollback; A/B    | Doubles resource use during cutover  |
| Reset & recompute    | Simple; single pipeline              | Downtime; no easy rollback           |

## Practical Notes

- Only works within the stream's retention period (events no longer in
  the log cannot be reprocessed)
- Watch for **side effects** — reprocessing may re-trigger emails, API
  calls, etc. unless outputs are idempotent
- Stateful applications must also reset/rebuild local state

## Source

Lecture 8, HSG ICS.
