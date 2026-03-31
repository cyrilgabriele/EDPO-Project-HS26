package ch.unisg.cryptoflow.transaction.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SpringDataOutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    Optional<OutboxEventEntity> findByTransactionId(String transactionId);

    List<OutboxEventEntity> findByPublishedAtIsNullAndCreatedAtBefore(Instant threshold);
}
