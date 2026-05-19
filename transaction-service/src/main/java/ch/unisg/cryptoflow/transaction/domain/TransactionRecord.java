package ch.unisg.cryptoflow.transaction.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param targetPrice         the bid price in USDT used by the matching topology
 * @param targetPriceDisplay  the price the user actually typed, in their Display Currency
 * @param displayCurrency     ISO-4217 currency the user submitted in
 */
public record TransactionRecord(
        String transactionId,
        String userId,
        String symbol,
        BigDecimal amount,
        BigDecimal targetPrice,
        BigDecimal targetPriceDisplay,
        String displayCurrency,
        TransactionStatus status,
        Instant placedAt,
        Instant resolvedAt,
        BigDecimal matchedPrice
) {}
