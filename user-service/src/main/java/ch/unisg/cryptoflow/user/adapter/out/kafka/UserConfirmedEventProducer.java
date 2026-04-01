package ch.unisg.cryptoflow.user.adapter.out.kafka;

import ch.unisg.cryptoflow.events.UserConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserConfirmedEventProducer {

    private final KafkaTemplate<String, UserConfirmedEvent> userConfirmedKafkaTemplate;

    @Value("${crypto.kafka.topic.user-confirmed}")
    private String topic;

    public void publish(UserConfirmedEvent event) {
        try {
            userConfirmedKafkaTemplate.send(topic, event.userId(), event).get();
            log.info("Published UserConfirmedEvent userId={}", event.userId());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing UserConfirmedEvent", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to publish UserConfirmedEvent for userId " + event.userId(), ex);
        }
    }
}
