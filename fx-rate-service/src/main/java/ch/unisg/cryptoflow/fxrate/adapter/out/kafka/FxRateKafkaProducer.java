package ch.unisg.cryptoflow.fxrate.adapter.out.kafka;

import ch.unisg.cryptoflow.events.avro.FxRate;
import ch.unisg.cryptoflow.fxrate.application.RecentFxRates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class FxRateKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(FxRateKafkaProducer.class);

    private final KafkaTemplate<String, FxRate> kafkaTemplate;
    private final String topic;
    private final RecentFxRates recent;

    @SuppressWarnings("unchecked")
    public FxRateKafkaProducer(
            KafkaTemplate<String, ?> kafkaTemplate,
            @Value("${reference.kafka.topic.fx-rate}") String topic,
            RecentFxRates recent) {
        this.kafkaTemplate = (KafkaTemplate<String, FxRate>) kafkaTemplate;
        this.topic = topic;
        this.recent = recent;
    }

    public void publish(FxRate event) {
        String key = event.getBase() + event.getQuote();
        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish FxRate for {}", key, ex);
                    } else {
                        int partition = result.getRecordMetadata().partition();
                        long offset = result.getRecordMetadata().offset();
                        log.debug("Published FxRate for {} offset={}", key, offset);
                        recent.record(new RecentFxRates.Entry(
                                key,
                                event.getBase(),
                                event.getQuote(),
                                event.getRate(),
                                event.getSource(),
                                event.getValidFromAt(),
                                event.getFetchedAt(),
                                Instant.now(),
                                partition,
                                offset));
                    }
                });
    }
}
