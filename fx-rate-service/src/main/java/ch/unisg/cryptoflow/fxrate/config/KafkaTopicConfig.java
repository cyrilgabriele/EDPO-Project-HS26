package ch.unisg.cryptoflow.fxrate.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.Map;

import static org.apache.kafka.common.config.TopicConfig.CLEANUP_POLICY_COMPACT;
import static org.apache.kafka.common.config.TopicConfig.CLEANUP_POLICY_CONFIG;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic referenceFxRateTopic(
            @Value("${reference.kafka.topic.fx-rate}") String name,
            @Value("${reference.kafka.topic.fx-rate-partitions:1}") int partitions) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(1)
                .configs(Map.of(CLEANUP_POLICY_CONFIG, CLEANUP_POLICY_COMPACT))
                .build();
    }
}
