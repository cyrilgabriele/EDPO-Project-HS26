package ch.unisg.cryptoflow.portfolio.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedTransactionRepository extends JpaRepository<ProcessedTransactionEntity, Long> {
}
