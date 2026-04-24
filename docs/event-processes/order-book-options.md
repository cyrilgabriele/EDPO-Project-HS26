# Order Book — Options for Discussion

**Status:** proposal, not yet committed.
**Purpose:** decide jointly whether to add an order-book scope, and if so which of the three shapes below.

## What an order book gives us

An L2 order book is the live list of resting bids and asks at each price level for a symbol. From a book we can derive:

- Best bid / best ask / bid-ask spread.
- Mid-price and micro-price (mid weighted by volume imbalance).
- Book imbalance — short-horizon directional signal used across HFT.
- Estimated execution price and slippage for a given order size.

## Binance access options

| Transport | Endpoint | What you get | Constraints |
|---|---|---|---|
| REST | `GET /api/v3/depth?symbol=BTCUSDT&limit=100` | one-shot snapshot, up to 5000 levels | weighted rate limit — tight under frequent polling; this is the "limited" surface |
| WebSocket | `<sym>@depth20@100ms` | top-20 levels, pushed every 100 ms | no polling cost, already on our infra |
| WebSocket | `<sym>@depth` | raw diffs | requires rebuilding a stateful book locally |

**Recommendation:** use **WebSocket `<sym>@depth20@100ms`**. market-data-service already subscribes to Binance WS for trades — adding another channel is a small extension. The REST endpoint isn't worth the rate-limit headache.

## Why this is on the table

The six committed scopes cover eight of the nine required patterns. The **one gap is Streaming Join** (pattern 15). A second stream (the order book) is the natural way to close it. It also opens the door to demonstrating **Large Events Patterns** (pattern 31) if we ever use full-depth snapshots.

## Option 1 — Best bid / ask derivation (stateless)

Map each L2 snapshot to a small `BestBidAsk{symbol, bid, ask, spread, midPrice}` event and publish to `crypto.book.bba`. Pure translator; fits inside scope 2's stateless chain.

- **Pattern:** Single-Event Processing, Event Translator.
- **Cost:** lowest.
- **Value:** BBA is useful for the dashboard.
- **Coverage:** does **not** close a gap — stateless is already covered.

## Option 2 — Execution-quality enrichment (stateful)

Materialize the book as a `top-of-book` KTable (key = symbol). On each `transaction.order.approved`, stream-table join against that KTable to emit `order.execution.quality{estimatedFillPrice, slippageBps, spreadAtExecution}`.

- **Pattern:** Stream-Table Join, State-Stream-Table Duality.
- **Cost:** medium.
- **Value:** actual operational signal (order-routing quality monitoring).
- **Coverage:** adds a second stream-table join example; does **not** close the streaming-join gap.

## Option 3 — Trade tape × book streaming join (windowed stream-stream join)

Join `crypto.price.clean` (trade ticks) with the book snapshot stream inside a small time window (≤ 500 ms) to classify each trade as buyer- or seller-initiated (aggressor inference).

- **Pattern:** **Streaming Join** (stream-stream, windowed) — **pattern 15**.
- **Cost:** medium-to-high.
- **Value:** HFT-flavored signal; strong narrative for the report.
- **Coverage:** **closes the only remaining gap** in the required-pattern list.

## Side-by-side

|  | Option 1 | Option 2 | Option 3 |
|---|---|---|---|
| Type | stateless | stateful | windowed (stream-stream join) |
| New required pattern unlocked | — | — | **15 Streaming Join** |
| Closes coverage gap? | no | no | **yes** |
| Implementation cost | low | medium | medium-high |
| Demo-ability | low | medium | high |

## Proposed path

1. **Always adopt Option 1 as a byproduct.** BBA derivation is cheap and useful regardless of which other option we pick.
2. **Choose between Option 2 and Option 3** based on how strongly we want to close the Streaming Join gap.
3. **Option 3 is the stronger report story** because it explicitly closes pattern 15. Option 2 is simpler and shipped faster.

## Open questions for the discussion

- [ ] Do we want to close the Streaming Join gap in this project, or accept it?
- [ ] Who owns the order-book extension (fits as a 7th scope; could be a joint deliverable)?
- [ ] If we go with Option 3, is a ≤ 500 ms window acceptable, or do we need to be tighter?
- [ ] Do we reconstruct the full book from `<sym>@depth` diffs, or stay with top-20 snapshots from `<sym>@depth20@100ms`? (Propose: top-20 snapshots — much simpler.)
- [ ] Is the added event volume (10 Hz per symbol × N symbols) OK for the local Kafka setup?

## Related

- Pattern reference: `docs/agent-instructions/events/15-streaming-join.md`.
- Related scopes: `02-price-stream-sanity.md` (for option 1), `04-portfolio-valuation.md` (stream-table-join pattern reuse for option 2).
