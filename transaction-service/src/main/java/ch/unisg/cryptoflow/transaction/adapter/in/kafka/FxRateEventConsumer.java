package ch.unisg.cryptoflow.transaction.adapter.in.kafka;

import ch.unisg.cryptoflow.events.avro.FxRate;
import ch.unisg.cryptoflow.transaction.domain.FxRateCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FxRateEventConsumer {

    private final FxRateCache cache;

    public FxRateEventConsumer(FxRateCache cache) {
        this.cache = cache;
    }

    @KafkaListener(
            topics = "${crypto.kafka.topic.fx-rate}",
            containerFactory = "fxRateListenerContainerFactory"
    )
    public void onFxRate(FxRate event) {
        if (event == null || event.getQuote() == null || event.getRate() == null) {
            log.warn("Received malformed FxRate event, skipping");
            return;
        }
        cache.update(event.getQuote().toString(), event.getRate());
    }
}
