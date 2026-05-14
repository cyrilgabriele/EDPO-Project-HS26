package ch.unisg.cryptoflow.transaction.adapter.out.persistence;

import ch.unisg.cryptoflow.transaction.avro.BuyBid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "matching_audit_buy_bid")
public class MatchingAuditBuyBidEntity {

    @Id
    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "bid_price", nullable = false, precision = 30, scale = 18)
    private BigDecimal bidPrice;

    @Column(name = "bid_quantity", nullable = false, precision = 30, scale = 18)
    private BigDecimal bidQuantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private String topic;

    @Column(name = "partition_id", nullable = false)
    private int partition;

    @Column(name = "offset_id", nullable = false)
    private long offset;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    protected MatchingAuditBuyBidEntity() {
    }

    public MatchingAuditBuyBidEntity(BuyBid bid, String topic, int partition, long offset, Instant consumedAt) {
        this.transactionId = bid.getTransactionId();
        updateFrom(bid, topic, partition, offset, consumedAt);
    }

    public void updateFrom(BuyBid bid, String topic, int partition, long offset, Instant consumedAt) {
        this.userId = bid.getUserId();
        this.symbol = bid.getSymbol();
        this.bidPrice = bid.getBidPrice();
        this.bidQuantity = bid.getBidQuantity();
        this.createdAt = bid.getCreatedAt();
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.consumedAt = consumedAt;
    }
}
