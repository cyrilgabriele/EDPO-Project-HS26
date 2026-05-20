package ch.unisg.cryptoflow.portfolio.streams;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.Map;

/**
 * Compacted output topic for the scope-04 portfolio valuation streams app.
 * Keyed by userId so the latest PortfolioValue per user is always retained.
 */
@Configuration
public class PortfolioValueTopicConfig {

    @Bean
    public NewTopic portfolioValueTopic(@Value("${crypto.kafka.topic.portfolio-value}") String name) {
        return TopicBuilder.name(name)
                .partitions(3)
                .replicas(1)
                .configs(Map.of(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT))
                .build();
    }
}
