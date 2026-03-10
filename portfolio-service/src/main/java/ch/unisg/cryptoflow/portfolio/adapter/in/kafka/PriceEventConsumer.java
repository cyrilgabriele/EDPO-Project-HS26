package ch.unisg.cryptoflow.portfolio.adapter.in.kafka;

import ch.unisg.cryptoflow.events.CryptoPriceUpdatedEvent;
import ch.unisg.cryptoflow.portfolio.domain.LocalPriceCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer adapter for {@code crypto.price.raw}.
 *
 * <p>Implements the <strong>Event-carried State Transfer</strong> pattern by
 * updating the {@link LocalPriceCache} on every received price event.
 * The portfolio service can then calculate current valuations from the cache
 * without making any synchronous call to {@code market-data-service}.
 */
@Component
@Slf4j
public class PriceEventConsumer {

    private final LocalPriceCache localPriceCache;

    public PriceEventConsumer(LocalPriceCache localPriceCache) {
        this.localPriceCache = localPriceCache;
    }

    @KafkaListener(
            topics = "${crypto.kafka.topic.price-raw}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPriceUpdated(CryptoPriceUpdatedEvent event) {
        if (event == null || event.symbol() == null || event.price() == null) {
            log.warn("Received null or malformed price event – skipping");
            return;
        }
        log.debug("Consumed price event: eventId={} symbol={} price={}",
                event.eventId(), event.symbol(), event.price());
        localPriceCache.updatePrice(event.symbol(), event.price());
    }
}
