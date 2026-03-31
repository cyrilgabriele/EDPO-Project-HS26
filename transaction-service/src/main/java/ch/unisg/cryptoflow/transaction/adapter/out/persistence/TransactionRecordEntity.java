package ch.unisg.cryptoflow.transaction.adapter.out.persistence;

import ch.unisg.cryptoflow.transaction.domain.TransactionRecord;
import ch.unisg.cryptoflow.transaction.domain.TransactionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transaction_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TransactionRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true, updatable = false)
    private String transactionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Column(name = "symbol", nullable = false, updatable = false)
    private String symbol;

    @Column(name = "amount", nullable = false, updatable = false, precision = 30, scale = 18)
    private BigDecimal amount;

    @Column(name = "target_price", nullable = false, updatable = false, precision = 30, scale = 8)
    private BigDecimal targetPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "matched_price", precision = 30, scale = 8)
    private BigDecimal matchedPrice;

    public static TransactionRecordEntity fromDomain(TransactionRecord record) {
        return new TransactionRecordEntity(
                null,
                record.transactionId(),
                record.userId(),
                record.symbol(),
                record.amount(),
                record.targetPrice(),
                record.status(),
                record.placedAt(),
                record.resolvedAt(),
                record.matchedPrice()
        );
    }

    public TransactionRecord toDomain() {
        return new TransactionRecord(
                transactionId, userId, symbol, amount, targetPrice,
                status, placedAt, resolvedAt, matchedPrice
        );
    }

    public void resolve(TransactionStatus newStatus, Instant at, BigDecimal price) {
        this.status = newStatus;
        this.resolvedAt = at;
        this.matchedPrice = price;
    }
}
