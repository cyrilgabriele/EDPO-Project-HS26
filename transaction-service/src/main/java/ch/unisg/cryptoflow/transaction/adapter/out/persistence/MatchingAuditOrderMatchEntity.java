package ch.unisg.cryptoflow.transaction.adapter.out.persistence;

import ch.unisg.cryptoflow.transaction.avro.OrderMatched;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "matching_audit_order_match")
public class MatchingAuditOrderMatchEntity {

    @Id
    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "ask_quote_id", nullable = false, unique = true)
    private String askQuoteId;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "matched_price", nullable = false, precision = 30, scale = 18)
    private BigDecimal matchedPrice;

    @Column(name = "matched_quantity", nullable = false, precision = 30, scale = 18)
    private BigDecimal matchedQuantity;

    @Column(name = "bid_created_at", nullable = false)
    private Instant bidCreatedAt;

    @Column(name = "ask_event_time", nullable = false)
    private Instant askEventTime;

    @Column(name = "source_venue", nullable = false)
    private String sourceVenue;

    @Column(nullable = false)
    private String topic;

    @Column(name = "partition_id", nullable = false)
    private int partition;

    @Column(name = "offset_id", nullable = false)
    private long offset;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    protected MatchingAuditOrderMatchEntity() {
    }

    public MatchingAuditOrderMatchEntity(OrderMatched match, String topic, int partition, long offset, Instant consumedAt) {
        this.transactionId = match.getTransactionId();
        updateFrom(match, topic, partition, offset, consumedAt);
    }

    public void updateFrom(OrderMatched match, String topic, int partition, long offset, Instant consumedAt) {
        this.askQuoteId = match.getAskQuoteId();
        this.symbol = match.getSymbol();
        this.matchedPrice = match.getMatchedPrice();
        this.matchedQuantity = match.getMatchedQuantity();
        this.bidCreatedAt = match.getBidCreatedAt();
        this.askEventTime = match.getAskEventTime();
        this.sourceVenue = match.getSourceVenue();
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.consumedAt = consumedAt;
    }
}
