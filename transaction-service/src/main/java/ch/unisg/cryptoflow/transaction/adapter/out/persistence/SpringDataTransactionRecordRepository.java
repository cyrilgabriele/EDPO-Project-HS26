package ch.unisg.cryptoflow.transaction.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataTransactionRecordRepository extends JpaRepository<TransactionRecordEntity, Long> {
    Optional<TransactionRecordEntity> findByTransactionId(String transactionId);
}
