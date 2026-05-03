package ch.unisg.cryptoflow.events;

import java.time.Instant;
import java.util.List;

/**
 * Published by the market-partial-book-ingestion-service to topic {@code crypto.scout.raw}.
 * Carries the raw top-N partial order-book depth update received from Binance.
 */
public record RawOrderBookDepthEvent(
        /** UUID – enables downstream idempotent processing. */
        String eventId,
        /** Trading pair symbol, e.g. {@code BTCUSDT}. Used as the Kafka message key. */
        String symbol,
        /** Binance event time from field {@code E}. */
        Instant eventTime,
        /** Binance transaction time from field {@code T}. */
        Instant transactionTime,
        /** First update ID from field {@code U}. */
        long firstUpdateId,
        /** Final update ID from field {@code u}. */
        long finalUpdateId,
        /** Previous final update ID from field {@code pu}. */
        long previousFinalUpdateId,
        /** Bid levels from field {@code b}. Kept in the raw topic for replayability. */
        List<OrderBookLevel> bids,
        /** Ask levels from field {@code a}. */
        List<OrderBookLevel> asks,
        /** Local service receive/process time. */
        Instant receivedAt
) {}
