package ch.unisg.cryptoflow.marketscout.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.Map;

/**
 * Programmatic Kafka topic creation for Market Scout derived events.
 *
 * <p>The scout topics are high-volume streaming feeds (partial book depth × symbols × levels).
 * The matching window in transaction-service is 35s — anything older is dead weight. Per-topic
 * retention is set tighter than the broker default so the broker doesn't accumulate gigabytes
 * of segments waiting on the 1h global default.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic cryptoScoutAskQuoteTopic(
            @Value("${crypto.kafka.topic.scout-ask-quotes}") String name,
            @Value("${crypto.kafka.topic.scout-derived-partitions:3}") int partitions,
            ScoutTopicRetention retention) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(1)
                .configs(retention.asConfigs())
                .build();
    }

    @Bean
    public NewTopic cryptoScoutAskOpportunityTopic(
            @Value("${crypto.kafka.topic.scout-ask-opportunities}") String name,
            @Value("${crypto.kafka.topic.scout-derived-partitions:3}") int partitions,
            ScoutTopicRetention retention) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(1)
                .configs(retention.asConfigs())
                .build();
    }

    @Bean
    public NewTopic cryptoScoutMatchableAskTopic(
            @Value("${crypto.kafka.topic.scout-matchable-asks}") String name,
            @Value("${crypto.kafka.topic.scout-derived-partitions:3}") int partitions,
            ScoutTopicRetention retention) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(1)
                .configs(retention.asConfigs())
                .build();
    }

    @Bean
    public NewTopic cryptoScoutWindowSummaryTopic(
            @Value("${crypto.kafka.topic.scout-window-summary}") String name,
            @Value("${crypto.kafka.topic.scout-derived-partitions:3}") int partitions,
            ScoutTopicRetention retention) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(1)
                .configs(retention.asConfigs())
                .build();
    }

    @Bean
    public ScoutTopicRetention scoutTopicRetention(
            @Value("${crypto.kafka.topic.scout-retention-ms:300000}") long retentionMs,
            @Value("${crypto.kafka.topic.scout-retention-bytes:52428800}") long retentionBytes,
            @Value("${crypto.kafka.topic.scout-segment-bytes:5242880}") long segmentBytes,
            @Value("${crypto.kafka.topic.scout-segment-ms:60000}") long segmentMs) {
        return new ScoutTopicRetention(retentionMs, retentionBytes, segmentBytes, segmentMs);
    }

    public record ScoutTopicRetention(long retentionMs, long retentionBytes, long segmentBytes, long segmentMs) {
        public Map<String, String> asConfigs() {
            return Map.of(
                    TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE,
                    TopicConfig.RETENTION_MS_CONFIG, Long.toString(retentionMs),
                    TopicConfig.RETENTION_BYTES_CONFIG, Long.toString(retentionBytes),
                    TopicConfig.SEGMENT_BYTES_CONFIG, Long.toString(segmentBytes),
                    TopicConfig.SEGMENT_MS_CONFIG, Long.toString(segmentMs)
            );
        }
    }
}
