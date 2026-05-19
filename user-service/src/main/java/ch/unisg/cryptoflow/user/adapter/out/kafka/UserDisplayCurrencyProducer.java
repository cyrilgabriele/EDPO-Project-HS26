package ch.unisg.cryptoflow.user.adapter.out.kafka;

import ch.unisg.cryptoflow.events.avro.UserDisplayCurrencyUpdated;
import ch.unisg.cryptoflow.user.application.port.out.PublishDisplayCurrencyPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class UserDisplayCurrencyProducer implements PublishDisplayCurrencyPort {

    private final KafkaTemplate<String, UserDisplayCurrencyUpdated> kafkaTemplate;
    private final String topic;

    public UserDisplayCurrencyProducer(
            KafkaTemplate<String, UserDisplayCurrencyUpdated> userDisplayCurrencyKafkaTemplate,
            @Value("${crypto.kafka.topic.user-display-currency}") String topic) {
        this.kafkaTemplate = userDisplayCurrencyKafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(String userId, String displayCurrency) {
        UserDisplayCurrencyUpdated event = UserDisplayCurrencyUpdated.newBuilder()
                .setUserId(userId)
                .setDisplayCurrency(displayCurrency)
                .setUpdatedAt(Instant.now())
                .build();
        try {
            kafkaTemplate.send(topic, userId, event).get();
            log.info("Published UserDisplayCurrencyUpdated userId={} displayCurrency={}", userId, displayCurrency);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing UserDisplayCurrencyUpdated", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to publish UserDisplayCurrencyUpdated for userId " + userId, ex);
        }
    }
}
