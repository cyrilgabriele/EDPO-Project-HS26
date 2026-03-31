package ch.unisg.cryptoflow.transaction.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionRecord(
        String transactionId,
        String userId,
        String symbol,
        BigDecimal amount,
        BigDecimal targetPrice,
        TransactionStatus status,
        Instant placedAt,
        Instant resolvedAt,
        BigDecimal matchedPrice
) {}
