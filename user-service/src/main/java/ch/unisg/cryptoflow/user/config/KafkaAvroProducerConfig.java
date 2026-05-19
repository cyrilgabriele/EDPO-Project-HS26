package ch.unisg.cryptoflow.user.config;

import ch.unisg.cryptoflow.events.avro.UserDisplayCurrencyUpdated;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Dedicated producer factory and template for the user.display-currency Avro
 * topic. The default Spring Kafka template stays JSON-configured so that the
 * existing UserConfirmedEvent and compensation flows are unaffected.
 */
@Configuration
public class KafkaAvroProducerConfig {

    @Bean
    public KafkaTemplate<String, UserDisplayCurrencyUpdated> userDisplayCurrencyKafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.properties.schema.registry.url:http://localhost:8090}") String schemaRegistryUrl) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }
}
