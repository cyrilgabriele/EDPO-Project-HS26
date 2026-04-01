package ch.unisg.cryptoflow.portfolio.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "processed_transaction")
public class ProcessedTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String transactionId;

    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedTransactionEntity() {}

    public ProcessedTransactionEntity(String transactionId, Instant processedAt) {
        this.transactionId = transactionId;
        this.processedAt = processedAt;
    }
}
