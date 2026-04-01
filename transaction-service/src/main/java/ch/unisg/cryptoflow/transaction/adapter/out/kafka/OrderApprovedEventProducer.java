package ch.unisg.cryptoflow.transaction.adapter.out.kafka;

import ch.unisg.cryptoflow.events.OrderApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderApprovedEventProducer {

    private final KafkaTemplate<String, OrderApprovedEvent> orderApprovedKafkaTemplate;

    @Value("${crypto.kafka.topic.order-approved}")
    private String topic;

    public void publish(OrderApprovedEvent event) {
        try {
            orderApprovedKafkaTemplate.send(topic, event.transactionId(), event).get();
            log.info("Published OrderApprovedEvent transactionId={} userId={} symbol={}",
                    event.transactionId(), event.userId(), event.symbol());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing OrderApprovedEvent", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to publish OrderApprovedEvent for transactionId=" + event.transactionId(), ex);
        }
    }
}
