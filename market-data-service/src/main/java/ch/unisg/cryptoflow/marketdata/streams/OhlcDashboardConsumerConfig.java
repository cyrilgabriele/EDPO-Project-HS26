package ch.unisg.cryptoflow.marketdata.streams;

import ch.unisg.cryptoflow.events.avro.Ohlc;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dedicated Avro consumer factory for the dashboard's read-back of the OHLC
 * topics. Uses a per-instance group id so each market-data-service start
 * rebuilds the in-memory latest-bar snapshot from offset 0. This is a separate
 * consumer from the {@link OhlcStreamConfig} streams app and does not
 * participate in its application id.
 */
@Configuration
@EnableKafka
public class OhlcDashboardConsumerConfig {

    private static final String INSTANCE_SUFFIX = "-" + UUID.randomUUID();

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Bean
    public ConsumerFactory<String, Ohlc> ohlcDashboardConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "market-data-ohlc-dashboard" + INSTANCE_SUFFIX);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Ohlc> ohlcDashboardListenerContainerFactory(
            ConsumerFactory<String, Ohlc> ohlcDashboardConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Ohlc> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(ohlcDashboardConsumerFactory);
        return factory;
    }
}
