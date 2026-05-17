# 31. Emit OHLC bars venue-native and convert at read time

Date: 2026-05-16

## Status

Accepted

## Context

The OHLC scope (see `docs/event-processes/05-ohlc-candles.md`) aggregates `crypto.price.clean` ticks into tumbling event-time windows and emits open/high/low/close bars. The source stream is USDT-denominated. With per-user Display Currency (ADR-0028), the bars could be:

1. Emitted **venue-native** (USDT) on a single topic per interval and converted at API read time.
2. Emitted **per currency** as separate topics (`crypto.ohlc.1m.USD`, `crypto.ohlc.1m.EUR`, …) by joining each tick against FX before aggregation.
3. Emitted as a single topic with a **map-per-bar** payload, where each of open/high/low/close carries a per-currency map.

A separate decision: should bars stream out continuously as a window fills (every contributing tick produces an update), or only when the window closes? The platform also wants to demonstrate the `suppress` operator (pattern 33, see `docs/agent-instructions/events/33-suppress-operator.md`).

## Decision

Emit OHLC bars **venue-native (USDT only)** on a single topic per interval (`crypto.ohlc.1m`, `crypto.ohlc.5m`, `crypto.ohlc.1h`), using `Ohlc` (Avro per ADR-0032), keyed by symbol. The streams app aggregates `crypto.price.clean` under tumbling windows with a custom event-time `TimestampExtractor` (pattern 32) and finalizes each window with `suppress(untilWindowCloses(unbounded()))`. One bar per symbol per window, emitted after the grace period elapses.

Display-currency conversion happens at API read time on the consuming service, using the current FX rate from the FX KTable. No live sibling topic (`crypto.ohlc.1m.live` or equivalent) is created at this time.

## Consequences

Closed-bar semantics match trading conventions: a `crypto.ohlc.1m` event always represents a finalized one-minute bar, never an in-flight one. Late-arriving ticks within the grace window contribute correctly; ticks arriving after the grace window are dropped (per scope-05 grace-period policy: 10 s for 1 m, 30 s for 5 m, 2 min for 1 h).

Charts are scale-invariant under affine transforms: converting historical USDT bars by the current FX rate keeps the bar shape correct and only rescales the y-axis, which is what a live trading dashboard wants. Historical-FX accuracy (showing what a bar was worth in EUR at its own event time, using the FX rate at that time) is explicitly out of scope; if needed later, an FX time series joined against historical bars would be a separate scope.

Avoiding per-currency topics keeps the topic count linear in interval count, not interval-count × currency-count, and avoids re-running aggregation for every supported currency. If the dashboard later needs progressively-updating rightmost candles, a `.live` sibling topic emitted without `suppress` can be added without breaking the closed-bar contract on the primary topic.
