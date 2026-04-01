package ch.unisg.cryptoflow.transaction.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public OutboxEventEntity(String eventId, String transactionId, String topic,
                             String payload, Instant createdAt) {
        this.eventId = eventId;
        this.transactionId = transactionId;
        this.topic = topic;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public void markPublished(Instant at) {
        this.publishedAt = at;
    }
}
