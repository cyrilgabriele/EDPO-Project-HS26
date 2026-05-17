package ch.unisg.cryptoflow.marketdata.streams;

import ch.unisg.cryptoflow.events.avro.Ohlc;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory snapshot of the latest closed OHLC bar per (symbol, intervalSec).
 * Updated by the dashboard's OHLC consumer; read by the dashboard endpoint.
 * Not part of the event pipeline, purely an observability aid for the static
 * demo page.
 */
@Component
public class RecentOhlcBars {

    private final Map<String, Entry> latest = new ConcurrentHashMap<>();

    public void record(Ohlc bar) {
        String key = bar.getSymbol() + "@" + bar.getIntervalSec();
        latest.put(key, new Entry(
                bar.getSymbol(),
                bar.getIntervalSec(),
                bar.getWindowStart(),
                bar.getWindowEnd(),
                bar.getOpen(),
                bar.getHigh(),
                bar.getLow(),
                bar.getClose(),
                bar.getTickCount(),
                Instant.now()));
    }

    public List<Entry> snapshot() {
        return latest.values().stream()
                .sorted(Comparator
                        .comparing(Entry::symbol)
                        .thenComparing(Entry::intervalSec))
                .toList();
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
