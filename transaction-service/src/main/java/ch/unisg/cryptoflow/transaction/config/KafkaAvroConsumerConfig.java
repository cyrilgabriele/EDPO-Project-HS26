package ch.unisg.cryptoflow.transaction.config;

import ch.unisg.cryptoflow.events.avro.FxRate;
import ch.unisg.cryptoflow.events.avro.UserDisplayCurrencyUpdated;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Avro consumer factories for {@code reference.fx.rate} and
 * {@code user.display-currency}. Each consumer uses a per-instance group id so
 * every transaction-service start rebuilds the in-memory KTable replicas from
 * offset 0 (compacted-topic-as-cache pattern). The existing JSON listener
 * factory in {@link KafkaConsumerConfig} stays the platform default.
 */
@Configuration
public class KafkaAvroConsumerConfig {

    private static final String INSTANCE_SUFFIX = "-" + UUID.randomUUID();

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    private Map<String, Object> baseProps(String groupIdPrefix) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupIdPrefix + INSTANCE_SUFFIX);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        return props;
    }

    @Bean
    public ConsumerFactory<String, FxRate> fxRateConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(baseProps("transaction-service-fx-rate"));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, FxRate> fxRateListenerContainerFactory(
            ConsumerFactory<String, FxRate> fxRateConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, FxRate> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(fxRateConsumerFactory);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, UserDisplayCurrencyUpdated> userDisplayCurrencyConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(baseProps("transaction-service-display-currency"));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserDisplayCurrencyUpdated> userDisplayCurrencyListenerContainerFactory(
            ConsumerFactory<String, UserDisplayCurrencyUpdated> userDisplayCurrencyConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, UserDisplayCurrencyUpdated> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(userDisplayCurrencyConsumerFactory);
        return factory;
    }
}
