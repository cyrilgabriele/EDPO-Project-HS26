package ch.unisg.cryptoflow.marketdata.application;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * In-memory ring buffer keeping the last {@value #MAX_SIZE} events published to Kafka.
 * Used exclusively by the dashboard endpoint – not part of core business logic.
 */
@Component
public class EventLog {

    private static final int MAX_SIZE = 20;

    public record Entry(
            String eventId,
            String symbol,
            BigDecimal price,
            Instant eventTimestamp,
            Instant publishedAt,
            int partition,
            long offset
    ) {}

    private final Deque<Entry> entries = new ArrayDeque<>();

    public synchronized void record(Entry entry) {
        if (entries.size() >= MAX_SIZE) {
            entries.pollFirst();
        }
        entries.addLast(entry);
    }

    /** Returns up to {@value #MAX_SIZE} entries, newest first. */
    public synchronized List<Entry> getRecent() {
        return List.copyOf(entries).reversed();
    }
}
