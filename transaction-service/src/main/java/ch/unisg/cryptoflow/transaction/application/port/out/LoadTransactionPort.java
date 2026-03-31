package ch.unisg.cryptoflow.transaction.application.port.out;

import ch.unisg.cryptoflow.transaction.domain.TransactionRecord;

import java.util.Optional;

public interface LoadTransactionPort {
    Optional<TransactionRecord> findByTransactionId(String transactionId);
}
