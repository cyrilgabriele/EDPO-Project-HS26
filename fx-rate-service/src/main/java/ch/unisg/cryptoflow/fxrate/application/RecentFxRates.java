package ch.unisg.cryptoflow.fxrate.application;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Thread-safe ring buffer of recently published FxRate events for the dashboard.
 * Not part of the event pipeline; purely an in-memory observability aid.
 */
@Component
public class RecentFxRates {

    private static final int CAPACITY = 50;

    private final Deque<Entry> entries = new ConcurrentLinkedDeque<>();

    public void record(Entry entry) {
        entries.addFirst(entry);
        while (entries.size() > CAPACITY) {
            entries.pollLast();
        }
    }

    public List<Entry> getRecent() {
        return List.copyOf((Collection<? extends Entry>) entries);
    }

    public record Entry(
            String pair,
            String base,
            String quote,
            BigDecimal rate,
            String source,
            Instant validFromAt,
            Instant fetchedAt,
            Instant publishedAt,
            int partition,
            long offset) {
    }
}
