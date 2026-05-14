package ch.unisg.cryptoflow.transaction.adapter.out.persistence;

import ch.unisg.cryptoflow.events.avro.MatchableAsk;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "matching_audit_matchable_ask")
public class MatchingAuditMatchableAskEntity {

    @Id
    @Column(name = "ask_quote_id", nullable = false)
    private String askQuoteId;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "ask_price", nullable = false, precision = 30, scale = 18)
    private BigDecimal askPrice;

    @Column(name = "ask_quantity", nullable = false, precision = 30, scale = 18)
    private BigDecimal askQuantity;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

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

    protected MatchingAuditMatchableAskEntity() {
    }

    public MatchingAuditMatchableAskEntity(MatchableAsk ask, String topic, int partition, long offset, Instant consumedAt) {
        this.askQuoteId = ask.getAskQuoteId();
        updateFrom(ask, topic, partition, offset, consumedAt);
    }

    public void updateFrom(MatchableAsk ask, String topic, int partition, long offset, Instant consumedAt) {
        this.symbol = ask.getSymbol();
        this.askPrice = ask.getAskPrice();
        this.askQuantity = ask.getAskQuantity();
        this.eventTime = ask.getEventTime();
        this.sourceVenue = ask.getSourceVenue();
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.consumedAt = consumedAt;
    }
}
