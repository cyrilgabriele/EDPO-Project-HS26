package ch.unisg.cryptoflow.marketpartialbookingestion.adapter.out.kafka;

import ch.unisg.cryptoflow.events.RawOrderBookDepthEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes raw partial book depth events to the replayable scout source topic.
 */
@Component
public class RawOrderBookDepthKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(RawOrderBookDepthKafkaProducer.class);

    private final KafkaTemplate<String, RawOrderBookDepthEvent> kafkaTemplate;
    private final String topic;

    @SuppressWarnings("unchecked")
    public RawOrderBookDepthKafkaProducer(
            KafkaTemplate<String, ?> kafkaTemplate,
            @Value("${crypto.kafka.topic.scout-raw}") String topic) {
        this.kafkaTemplate = (KafkaTemplate<String, RawOrderBookDepthEvent>) kafkaTemplate;
        this.topic = topic;
    }

    public void publish(RawOrderBookDepthEvent event) {
        kafkaTemplate.send(topic, event.symbol(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish raw order-book depth event for symbol {}", event.symbol(), ex);
                    } else {
                        log.debug("Published raw order-book depth event for {} -> partition={} offset={}",
                                event.symbol(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
