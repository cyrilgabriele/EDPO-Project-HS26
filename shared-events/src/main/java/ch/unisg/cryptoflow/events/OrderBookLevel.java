package ch.unisg.cryptoflow.events;

import java.math.BigDecimal;

/**
 * One price level in an order book.
 */
public record OrderBookLevel(
        BigDecimal price,
        BigDecimal quantity
) {}
