# Market Order Scout

Market Order Scout is the CryptoFlow service area that ingests bounded futures order-book snapshots and derives ask-side signals for stream-processing demonstrations.

## Language

**Market Scout**:
A flow that ingests bounded futures order-book snapshots for configured trading symbols and derives ask-side signals from them.
_Avoid_: Price stream, ticker stream

**Order Book Snapshot**:
A bounded view of bid and ask levels for a trading symbol at a point in exchange time.
_Avoid_: Price tick

**Ask Quote**:
One sell-side order-book level with a price and quantity.
_Avoid_: Ask price when quantity also matters

**Ask Opportunity**:
An ask quote that satisfies the configured scout threshold.
_Avoid_: Trade, order

## Relationships

- A **Market Scout** observes one or more **Order Book Snapshots**.
- An **Order Book Snapshot** contains bid and ask levels.
- Only ask levels become **Ask Quotes** in the scout topology.
- An **Ask Opportunity** is derived from exactly one **Ask Quote**.

## Example dialogue

> **Dev:** "Can we reuse the existing price event for the scout?"
> **Domain expert:** "No, the **Market Scout** works from **Ask Quotes** in the order book, not ticker prices used for portfolio valuation."

## Flagged ambiguities

- "price stream" was used for both ticker prices and order-book asks - resolved: the scout flow uses **Ask Quotes**, not ticker price events.
