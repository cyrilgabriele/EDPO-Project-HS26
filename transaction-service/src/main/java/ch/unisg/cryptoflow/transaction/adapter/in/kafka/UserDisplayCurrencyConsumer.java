package ch.unisg.cryptoflow.transaction.adapter.in.kafka;

import ch.unisg.cryptoflow.events.avro.UserDisplayCurrencyUpdated;
import ch.unisg.cryptoflow.transaction.domain.DisplayCurrencyCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserDisplayCurrencyConsumer {

    private final DisplayCurrencyCache cache;

    public UserDisplayCurrencyConsumer(DisplayCurrencyCache cache) {
        this.cache = cache;
    }

    @KafkaListener(
            topics = "${crypto.kafka.topic.user-display-currency}",
            containerFactory = "userDisplayCurrencyListenerContainerFactory"
    )
    public void onDisplayCurrencyUpdated(UserDisplayCurrencyUpdated event) {
        if (event == null || event.getUserId() == null || event.getDisplayCurrency() == null) {
            log.warn("Received malformed UserDisplayCurrencyUpdated event, skipping");
            return;
        }
        cache.update(event.getUserId().toString(), event.getDisplayCurrency().toString());
    }
}
