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

**Ask Quote ID**:
A deterministic id for one ask level, derived from the raw depth event identity and the ask level index. It identifies an exchange book level observation, not a seller.
_Avoid_: askerId

**Matchable Ask**:
The cross-service Avro ask contract published by Market Scout for transaction matching. It carries `askQuoteId`, normalized symbol, ask price, ask quantity, exchange event time, and source venue.
_Avoid_: Ask Opportunity when matching is independent from the scout threshold

**Ask Opportunity**:
An ask quote that satisfies the configured scout threshold.
_Avoid_: Trade, order

**Buy Bid**:
A user-submitted buy order candidate emitted by transaction-service after form validation and persistence. It is keyed by normalized Binance symbol and competes for Matchable Asks.
_Avoid_: PendingOrder when referring to the stream event

**Order Validity Window**:
The 30-second event-time interval starting at `BuyBid.createdAt` during which a Matchable Ask may approve the bid. The Camunda rejection timer is 35 seconds to cover the validity interval plus a 5-second grace/fallback margin.
_Avoid_: Processing-time timeout for matching eligibility

## Relationships

- A **Market Scout** observes one or more **Order Book Snapshots**.
- An **Order Book Snapshot** contains bid and ask levels.
- Only ask levels become **Ask Quotes** in the scout topology.
- An **Ask Opportunity** is derived from exactly one **Ask Quote**.
- A **Matchable Ask** is derived from exactly one **Ask Quote** and is keyed by normalized symbol.
- A **Buy Bid** can match one **Matchable Ask** during its **Order Validity Window**.
- A **Matchable Ask** can be allocated to at most one winning **Buy Bid**.

## Example dialogue

> **Dev:** "Can we reuse the existing price event for the scout?"
> **Domain expert:** "No, the **Market Scout** works from **Ask Quotes** in the order book, not ticker prices used for portfolio valuation."

## Flagged ambiguities

- "price stream" was used for both ticker prices and order-book asks - resolved: the scout flow uses **Ask Quotes**, not ticker price events.
- `bestAskPrice` and `bestAskQuantity` mean the best ask from the same raw order-book snapshot, not a time-window aggregate.
