# 25. Add derived market features to Market Scout events

Date: 2026-05-03

## Status

Accepted

## Context

ADR-0021 selected Binance USD-M Futures Partial Book Depth Streams as the Market Scout input, and ADR-0022 kept `crypto.scout.raw` as the replayable JSON capture while introducing Avro contracts for derived Market Scout events.

The raw partial-depth event already contains enough ask-side information to derive basic ask liquidity features without maintaining order-book state. These features are useful for ask quote and opportunity analysis, but they belong to the project-owned derived event contracts rather than to the exchange-facing raw ingestion contract.

## Decision

Keep `crypto.scout.raw` unchanged as the replayable JSON capture of Binance partial book data.

Extend the derived Avro events `AskQuote` and `AskOpportunity` with computed ask-side market feature fields. Do not extend `ScoutWindowSummary` for now, because the current window summary remains an aggregate over ask opportunities rather than a contract for top-of-book metrics.

Compute the new fields statelessly from the validated ask side of each raw partial-depth event before flattening ask levels:

- `bestAskPrice`: price from the first valid ask level.
- `bestAskQuantity`: quantity from the first valid ask level.
- `askNotional`: `ask.price * ask.quantity` for each flattened ask quote.

Copy these derived fields from `AskQuote` to `AskOpportunity` when an ask quote passes the opportunity filter.

Bid-side fields are intentionally excluded from the derived Market Scout contracts. The current Market Scout stream models ask quotes and ask opportunities, and adding bid-dependent metrics would couple those ask-focused events to data they do not otherwise need.

## Consequences

Derived Market Scout events become more useful for ask liquidity and short-horizon ask opportunity analysis while the raw Binance capture remains stable and replayable.

The best ask features are repeated on each flattened `AskQuote` from the same raw book event. This is acceptable for the current topology because it emits one quote per ask level, and repeating the source event's ask context keeps each derived quote self-contained.

The Avro contracts change, so tests and generated Avro classes must be updated when this implementation is applied.

If bid-aware or full top-of-book metrics later need their own lifecycle, the project can introduce a separate top-of-book topic instead of adding bid-dependent fields to ask-focused events.
