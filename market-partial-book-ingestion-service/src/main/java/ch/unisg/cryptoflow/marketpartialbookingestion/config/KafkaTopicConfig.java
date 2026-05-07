package ch.unisg.cryptoflow.marketpartialbookingestion.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Programmatic Kafka topic creation for raw Market Scout source events.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic cryptoScoutRawTopic(
            @Value("${crypto.kafka.topic.scout-raw}") String name,
            @Value("${crypto.kafka.topic.scout-raw-partitions:3}") int partitions) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(1)
                .build();
    }

}
