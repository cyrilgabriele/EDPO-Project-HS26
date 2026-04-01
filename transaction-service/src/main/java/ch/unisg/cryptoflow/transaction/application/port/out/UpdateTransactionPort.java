package ch.unisg.cryptoflow.transaction.application.port.out;

import ch.unisg.cryptoflow.transaction.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public interface UpdateTransactionPort {
    void updateStatus(String transactionId, TransactionStatus status, Instant resolvedAt, BigDecimal matchedPrice);
}
