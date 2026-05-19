package ch.unisg.cryptoflow.coinmetadata.application;

import ch.unisg.cryptoflow.coinmetadata.adapter.in.provider.CoinMetadataFetch;
import ch.unisg.cryptoflow.coinmetadata.adapter.in.provider.CoinMetadataProvider;
import ch.unisg.cryptoflow.coinmetadata.adapter.out.kafka.CoinMetadataKafkaProducer;
import ch.unisg.cryptoflow.events.avro.CoinMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled poll of the metadata provider. The first run fires shortly after
 * startup so the compacted topic has data before the OHLC streams app builds
 * its GlobalKTable replica. Subsequent runs refresh slowly (coin metadata is
 * effectively static aside from market-cap ranking).
 */
@Component
public class CoinMetadataPoller {

    private static final Logger log = LoggerFactory.getLogger(CoinMetadataPoller.class);

    private final CoinMetadataProvider provider;
    private final CoinMetadataMapper mapper;
    private final CoinMetadataKafkaProducer producer;

    public CoinMetadataPoller(CoinMetadataProvider provider,
                              CoinMetadataMapper mapper,
                              CoinMetadataKafkaProducer producer) {
        this.provider = provider;
        this.mapper = mapper;
        this.producer = producer;
    }

    @Scheduled(fixedDelayString = "${coin.poll-interval}", initialDelayString = "PT15S")
    public void poll() {
        try {
            List<CoinMetadataFetch> fetches = provider.fetchAll();
            if (fetches.isEmpty()) {
                log.warn("Provider returned no entries, nothing to publish");
                return;
            }
            List<CoinMetadata> events = mapper.toEvents(fetches);
            events.forEach(producer::publish);
            log.info("Published {} CoinMetadata events", events.size());
        } catch (Exception ex) {
            log.error("Coin metadata poll failed", ex);
        }
    }
}
