package ch.unisg.cryptoflow.marketscout.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Programmatic Kafka topic creation for Market Scout derived events.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic cryptoScoutAskQuoteTopic(
            @Value("${crypto.kafka.topic.scout-ask-quotes}") String name,
            @Value("${crypto.kafka.topic.scout-derived-partitions:3}") int partitions) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic cryptoScoutAskOpportunityTopic(
            @Value("${crypto.kafka.topic.scout-ask-opportunities}") String name,
            @Value("${crypto.kafka.topic.scout-derived-partitions:3}") int partitions) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic cryptoScoutMatchableAskTopic(
            @Value("${crypto.kafka.topic.scout-matchable-asks}") String name,
            @Value("${crypto.kafka.topic.scout-derived-partitions:3}") int partitions) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic cryptoScoutWindowSummaryTopic(
            @Value("${crypto.kafka.topic.scout-window-summary}") String name,
            @Value("${crypto.kafka.topic.scout-derived-partitions:3}") int partitions) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(1)
                .build();
    }
}
