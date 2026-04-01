package ch.unisg.cryptoflow.transaction.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Atomically updates the transaction status to APPROVED and inserts
 * an outbox event row in the same local DB transaction (ADR-0014).
 */
public interface ApproveTransactionPort {

    void approveWithOutbox(String transactionId,
                           Instant resolvedAt,
                           BigDecimal matchedPrice,
                           String outboxEventId,
                           String outboxTopic,
                           String outboxPayload);
}
