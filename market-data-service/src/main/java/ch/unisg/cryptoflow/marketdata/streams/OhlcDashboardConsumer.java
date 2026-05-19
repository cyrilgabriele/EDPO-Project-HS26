package ch.unisg.cryptoflow.marketdata.streams;

import ch.unisg.cryptoflow.events.avro.Ohlc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to all three OHLC output topics and feeds the
 * {@link RecentOhlcBars} snapshot used by the dashboard. Separate from the
 * scope-05 streams app: this is a plain consumer that reads the published
 * bars back from Kafka.
 */
@Component
@Slf4j
public class OhlcDashboardConsumer {

    private final RecentOhlcBars recent;

    public OhlcDashboardConsumer(RecentOhlcBars recent) {
        this.recent = recent;
    }

    @KafkaListener(
            topics = {
                    "${crypto.kafka.topic.ohlc-1m}",
                    "${crypto.kafka.topic.ohlc-5m}",
                    "${crypto.kafka.topic.ohlc-1h}"
            },
            containerFactory = "ohlcDashboardListenerContainerFactory"
    )
    public void onOhlc(Ohlc bar) {
        if (bar == null || bar.getSymbol() == null) {
            log.warn("Received null or malformed Ohlc bar, skipping");
            return;
        }
        log.debug("Consumed Ohlc bar symbol={} interval={}s close={} ticks={}",
                bar.getSymbol(), bar.getIntervalSec(), bar.getClose(), bar.getTickCount());
        recent.record(bar);
    }
}
