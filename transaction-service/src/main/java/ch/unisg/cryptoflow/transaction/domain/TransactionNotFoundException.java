package ch.unisg.cryptoflow.transaction.domain;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(String transactionId) {
        super("Transaction record not found or already resolved: " + transactionId);
    }
}
