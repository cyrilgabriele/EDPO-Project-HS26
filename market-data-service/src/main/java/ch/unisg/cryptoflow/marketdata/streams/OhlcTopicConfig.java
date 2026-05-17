package ch.unisg.cryptoflow.marketdata.streams;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.Map;

/**
 * Output topics for the scope-05 OHLC streams app. Retention per interval follows
 * 05-ohlc-candles.md: 7 days for 1m, 30 days for 5m, 365 days for 1h.
 */
@Configuration
public class OhlcTopicConfig {

    @Bean
    public NewTopic ohlc1mTopic(@Value("${crypto.kafka.topic.ohlc-1m}") String name) {
        return TopicBuilder.name(name)
                .partitions(3)
                .replicas(1)
                .configs(Map.of(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(7L * 24 * 3600 * 1000)))
                .build();
    }

    @Bean
    public NewTopic ohlc5mTopic(@Value("${crypto.kafka.topic.ohlc-5m}") String name) {
        return TopicBuilder.name(name)
                .partitions(3)
                .replicas(1)
                .configs(Map.of(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(30L * 24 * 3600 * 1000)))
                .build();
    }

    @Bean
    public NewTopic ohlc1hTopic(@Value("${crypto.kafka.topic.ohlc-1h}") String name) {
        return TopicBuilder.name(name)
                .partitions(3)
                .replicas(1)
                .configs(Map.of(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(365L * 24 * 3600 * 1000)))
                .build();
    }
}
