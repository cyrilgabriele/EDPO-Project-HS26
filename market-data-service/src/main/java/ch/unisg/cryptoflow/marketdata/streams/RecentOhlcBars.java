package ch.unisg.cryptoflow.marketdata.streams;

import ch.unisg.cryptoflow.events.avro.Ohlc;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * In-memory rolling history of closed OHLC bars per (symbol, intervalSec).
 * Bounded to {@link #HISTORY_LIMIT} entries per pair so the dashboard can
 * render a candlestick chart without unbounded memory growth.
 *
 * <p>Bars are keyed inside each series by their event-time {@code windowStart},
 * so re-reading the topic on restart (per-instance group id) replaces rather
 * than duplicates entries.
 */
@Component
public class RecentOhlcBars {

    private static final int HISTORY_LIMIT = 200;

    private final Map<String, NavigableMap<Long, Entry>> history = new ConcurrentHashMap<>();
    private final Map<String, Entry> latestPerPair = new ConcurrentHashMap<>();

    public void record(Ohlc bar) {
        String key = bar.getSymbol() + "@" + bar.getIntervalSec();
        long startMs = bar.getWindowStart().toEpochMilli();
        Entry entry = new Entry(
                bar.getSymbol(),
                bar.getIntervalSec(),
                bar.getWindowStart(),
                bar.getWindowEnd(),
                bar.getOpen(),
                bar.getHigh(),
                bar.getLow(),
                bar.getClose(),
                bar.getTickCount(),
                Instant.now());

        latestPerPair.put(key, entry);

        NavigableMap<Long, Entry> series = history.computeIfAbsent(key, k -> new ConcurrentSkipListMap<>());
        series.put(startMs, entry);
        while (series.size() > HISTORY_LIMIT) {
            Long oldest = series.firstKey();
            if (oldest == null) break;
            series.remove(oldest);
        }
    }

    /** Latest bar per (symbol, interval) for the at-a-glance summary. */
    public List<Entry> latestSnapshot() {
        return latestPerPair.values().stream()
                .sorted(Comparator
                        .comparing(Entry::symbol)
                        .thenComparing(Entry::intervalSec))
                .toList();
    }

    /** Full bounded history per "symbol@intervalSec" key, sorted ascending by windowStart. */
    public Map<String, List<Entry>> historySnapshot() {
        Map<String, List<Entry>> out = new LinkedHashMap<>();
        history.forEach((k, v) -> out.put(k, List.copyOf(v.values())));
        return out;
    }

    public record Entry(
            String symbol,
            int intervalSec,
            Instant windowStart,
            Instant windowEnd,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            int tickCount,
            Instant receivedAt) {
    }
}
