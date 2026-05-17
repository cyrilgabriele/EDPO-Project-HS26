package ch.unisg.cryptoflow.transaction.adapter.in.kafka;

import ch.unisg.cryptoflow.events.CryptoPriceUpdatedEvent;
import ch.unisg.cryptoflow.transaction.domain.LocalPriceCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code crypto.price.raw} into a local USDT price cache so the
 * buy-time quote endpoint can render the latest price without a synchronous
 * call to market-data-service.
 */
@Slf4j
@Component
public class PriceEventConsumer {

    private final LocalPriceCache cache;

    public PriceEventConsumer(LocalPriceCache cache) {
        this.cache = cache;
    }

    @KafkaListener(
            topics = "${crypto.kafka.topic.price-raw}",
            groupId = "${spring.kafka.consumer.group-id}-price",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPriceUpdated(CryptoPriceUpdatedEvent event) {
        if (event == null || event.symbol() == null || event.price() == null) {
            log.warn("Received null or malformed price event, skipping");
            return;
        }
        cache.updatePrice(event.symbol(), event.price());
    }
}
