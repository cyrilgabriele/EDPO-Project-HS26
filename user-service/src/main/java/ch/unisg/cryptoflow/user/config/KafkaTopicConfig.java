package ch.unisg.cryptoflow.user.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic portfolioCompensationTopic(
        @Value("${crypto.kafka.topic.portfolio-compensation}") String topicName
    ) {
        return TopicBuilder.name(topicName)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic userCompensationTopic(
        @Value("${crypto.kafka.topic.user-compensation}") String topicName
    ) {
        return TopicBuilder.name(topicName)
            .partitions(3)
            .replicas(1)
            .build();
    }

    /**
     * Compacted topic: only the latest event per userId (key) is retained.
     * Consumers can reconstruct the full set of confirmed users by reading
     * from the beginning — even after the 1-hour dev retention window has passed.
     */
    @Bean
    public NewTopic userConfirmedTopic(
        @Value("${crypto.kafka.topic.user-confirmed}") String topicName
    ) {
        return TopicBuilder.name(topicName)
            .partitions(3)
            .replicas(1)
            .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
            .build();
    }
}
