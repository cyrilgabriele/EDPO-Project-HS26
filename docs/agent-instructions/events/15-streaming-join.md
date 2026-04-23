# Pattern: Streaming Join (Windowed Join)

Joining **two streams**. Unlike tables, streams have no "current state" to
join — we match events across the two streams that share a key and fall
within a common time window. This is a **windowed join**.

## Definition

When we join two streams, we join the entire history, trying to match
events in one stream with events in the other that:

1. Have the **same key**, and
2. Occurred within the **same time window**

## Example

Clicks stream joined with Searches stream on user ID over a 5-second window:

```
Clicks:   U:15  U:19  U:22  U:30  U:12  U:3  [U:43]  U:23  U:5  U:18
                  ├────── 5-second window ──────┤
                                                      Local State
                                                           ↕
                                                      Joined event
                                                           ↕
                                                      Local State
                           ├───── 5-second window ─────┤
Searches: U:17  U:29  U:25  U:33  U:24  U:17  U:55  [U:43]  U:9  U:48
```

Events with `U:43` in both streams occur within the same window → emit
joined event.

## Implementation Requirements

- Each side maintains **local state** of recent events (within window)
- State grows with window size × event rate
- Windows slide as time advances; events leave state when out-of-window

## Use Cases

- Correlating clicks with searches in ad-tech
- Matching orders with shipment acknowledgements
- Detecting missing counterparts (stream-stream left join with null)

## Trade-offs

- ✅ Handles streams without requiring materialization
- ❌ Window size determines state size and memory footprint
- ❌ Late events may miss their window (handle via grace period)

## Source

Lecture 8, HSG ICS.
