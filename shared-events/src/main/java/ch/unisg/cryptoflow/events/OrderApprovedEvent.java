package ch.unisg.cryptoflow.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published by {@code transaction-service} to topic {@code transaction.order.approved}
 * when a pending order's target price is matched.
 *
 * <p>Consumed by {@code portfolio-service} to upsert the user's holding
 * (Event-Carried State Transfer pattern – ADR-0002).
 */
public record OrderApprovedEvent(
        /** UUID of the approved transaction. */
        String transactionId,
        /** Owner of the order. */
        String userId,
        /** Trading pair symbol, e.g. {@code BTCUSDT}. */
        String symbol,
        /** Quantity purchased. */
        BigDecimal amount,
        /** Price at which the order was matched. */
        BigDecimal matchedPrice,
        /** Timestamp when the approval was recorded (UTC). */
        Instant approvedAt
) {}
