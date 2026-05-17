package ch.unisg.cryptoflow.marketdata.streams;

import ch.unisg.cryptoflow.events.CryptoPriceUpdatedEvent;
import ch.unisg.cryptoflow.events.avro.Ohlc;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pure aggregation helpers for scope-05 OHLC bars. No Spring, no Kafka Streams
 * APIs: just an {@link Ohlc} initializer and an update step that folds one
 * tick into the current bar.
 *
 * <p>The window metadata (interval and start/end timestamps) is filled in by
 * the topology after {@code suppress(untilWindowCloses)} so that the aggregator
 * stays unaware of the windowing strategy.
 */
public final class OhlcAggregator {

    private OhlcAggregator() {}

    /** Initial state before any tick has been seen. */
    public static Ohlc empty() {
        return Ohlc.newBuilder()
                .setSymbol("")
                .setIntervalSec(0)
                .setWindowStart(Instant.EPOCH)
                .setWindowEnd(Instant.EPOCH)
                .setOpen(BigDecimal.ZERO)
                .setHigh(BigDecimal.ZERO)
                .setLow(BigDecimal.ZERO)
                .setClose(BigDecimal.ZERO)
                .setTickCount(0)
                .build();
    }

    /** Folds one tick into the running bar. Returns the same {@code bar} instance, mutated. */
    public static Ohlc update(String symbol, CryptoPriceUpdatedEvent tick, Ohlc bar) {
        BigDecimal price = tick.price();
        if (bar.getTickCount() == 0) {
            bar.setSymbol(symbol);
            bar.setOpen(price);
            bar.setHigh(price);
            bar.setLow(price);
            bar.setClose(price);
        } else {
            if (price.compareTo(bar.getHigh()) > 0) bar.setHigh(price);
            if (price.compareTo(bar.getLow()) < 0)  bar.setLow(price);
            bar.setClose(price);
        }
        bar.setTickCount(bar.getTickCount() + 1);
        return bar;
    }
}
