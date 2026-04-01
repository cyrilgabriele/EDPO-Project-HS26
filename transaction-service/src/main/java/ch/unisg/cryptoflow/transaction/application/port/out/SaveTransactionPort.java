package ch.unisg.cryptoflow.transaction.application.port.out;

import ch.unisg.cryptoflow.transaction.domain.TransactionRecord;

public interface SaveTransactionPort {
    TransactionRecord save(TransactionRecord record);
}
