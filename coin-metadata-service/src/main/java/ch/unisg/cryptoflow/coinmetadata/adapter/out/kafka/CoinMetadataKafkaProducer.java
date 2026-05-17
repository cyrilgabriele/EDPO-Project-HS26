package ch.unisg.cryptoflow.coinmetadata.adapter.out.kafka;

import ch.unisg.cryptoflow.events.avro.CoinMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CoinMetadataKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(CoinMetadataKafkaProducer.class);

    private final KafkaTemplate<String, CoinMetadata> kafkaTemplate;
    private final String topic;

    @SuppressWarnings("unchecked")
    public CoinMetadataKafkaProducer(
            KafkaTemplate<String, ?> kafkaTemplate,
            @Value("${reference.kafka.topic.crypto-metadata}") String topic) {
        this.kafkaTemplate = (KafkaTemplate<String, CoinMetadata>) kafkaTemplate;
        this.topic = topic;
    }

    public void publish(CoinMetadata event) {
        String key = event.getSymbol().toString();
        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish CoinMetadata for {}", key, ex);
                    } else {
                        log.debug("Published CoinMetadata for {} offset={}",
                                key, result.getRecordMetadata().offset());
                    }
                });
    }
}
