# 21. Use Binance Partial Book Depth Streams for Market Scout

Date: 2026-05-03

## Status

Accepted

## Context

The Market Scout needs a continuous futures order-book input so it can derive ask-side signals and later demonstrate Kafka Streams filters, translation, Avro contracts, and windowed processing. Binance offers three relevant choices: the WebSocket API `depth` request, Partial Book Depth Streams, and Diff Book Depth Streams.

## Decision

We use Binance USD-M Futures Partial Book Depth Streams for the MVP. The WebSocket API `depth` endpoint is request/response rather than a continuous market stream, so it does not fit the event-processing topology. Diff Book Depth Streams are more precise for reconstructing a local order book, but require initial snapshot reconciliation, update-sequence handling, and recovery logic. Partial Book Depth Streams provide bounded top-N bid and ask levels with exchange timestamps, which matches the MVP topology: filter to asks, translate to scout events, apply threshold filters, and compute windowed aggregates.

## Consequences

The Market Scout observes only the configured top-N book levels, not the complete order book. It can miss valid ask opportunities outside the configured depth, and we accept this limitation for the MVP because the goal is to demonstrate the stream-processing topology rather than full order-book reconstruction. A future full-order-book implementation should combine a WebSocket API `depth` snapshot with Diff Book Depth Streams, validate update sequences, recover from gaps, and maintain a local materialized order book before deriving ask opportunities.
