package ch.unisg.cryptoflow.portfolio.config;

import ch.unisg.cryptoflow.events.CryptoPriceUpdatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

/**
 * Kafka consumer configuration for the portfolio service.
 *
 * <p>The listener factory is named {@code kafkaListenerContainerFactory} (the
 * Spring default) so that {@code @KafkaListener} beans pick it up automatically.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, CryptoPriceUpdatedEvent> priceEventConsumerFactory() {
        JsonDeserializer<CryptoPriceUpdatedEvent> deserializer =
                new JsonDeserializer<>(CryptoPriceUpdatedEvent.class);
        deserializer.addTrustedPackages("ch.unisg.cryptoflow.events");
        deserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                        ConsumerConfig.GROUP_ID_CONFIG, groupId,
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
                ),
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CryptoPriceUpdatedEvent>
    kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CryptoPriceUpdatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(priceEventConsumerFactory());
        return factory;
    }
}
