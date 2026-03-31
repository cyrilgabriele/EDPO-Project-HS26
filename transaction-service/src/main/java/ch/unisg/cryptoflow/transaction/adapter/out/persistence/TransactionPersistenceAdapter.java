package ch.unisg.cryptoflow.transaction.adapter.out.persistence;

import ch.unisg.cryptoflow.transaction.application.port.out.ApproveTransactionPort;
import ch.unisg.cryptoflow.transaction.application.port.out.LoadTransactionPort;
import ch.unisg.cryptoflow.transaction.application.port.out.SaveTransactionPort;
import ch.unisg.cryptoflow.transaction.application.port.out.UpdateTransactionPort;
import ch.unisg.cryptoflow.transaction.domain.TransactionNotFoundException;
import ch.unisg.cryptoflow.transaction.domain.TransactionRecord;
import ch.unisg.cryptoflow.transaction.domain.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TransactionPersistenceAdapter
        implements SaveTransactionPort, LoadTransactionPort, UpdateTransactionPort, ApproveTransactionPort {

    private final SpringDataTransactionRecordRepository repository;
    private final SpringDataOutboxEventRepository outboxRepository;

    @Override
    @Transactional
    public TransactionRecord save(TransactionRecord record) {
        return repository.save(TransactionRecordEntity.fromDomain(record)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TransactionRecord> findByTransactionId(String transactionId) {
        return repository.findByTransactionId(transactionId).map(TransactionRecordEntity::toDomain);
    }

    @Override
    @Transactional
    public void updateStatus(String transactionId, TransactionStatus status, Instant resolvedAt, BigDecimal matchedPrice) {
        repository.findByTransactionId(transactionId)
                .ifPresent(entity -> entity.resolve(status, resolvedAt, matchedPrice));
    }

    /**
     * Atomically marks the transaction APPROVED and inserts an outbox event row
     * in the same local DB transaction (ADR-0014 — Outbox Pattern).
     */
    @Override
    @Transactional
    public void approveWithOutbox(String transactionId, Instant resolvedAt, BigDecimal matchedPrice,
                                  String outboxEventId, String outboxTopic, String outboxPayload) {
        TransactionRecordEntity entity = repository.findByTransactionId(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
        entity.resolve(TransactionStatus.APPROVED, resolvedAt, matchedPrice);

        outboxRepository.save(new OutboxEventEntity(outboxEventId, transactionId, outboxTopic,
                outboxPayload, resolvedAt));
    }
}
