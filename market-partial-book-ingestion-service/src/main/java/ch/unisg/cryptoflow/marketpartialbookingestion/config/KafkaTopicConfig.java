package ch.unisg.cryptoflow.marketpartialbookingestion.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.Map;

/**
 * Programmatic Kafka topic creation for raw Market Scout source events.
 *
 * <p>{@code crypto.scout.raw} ingests Binance partial book depth updates at ~250ms cadence per
 * symbol. At default settings (6 symbols × 20 depth levels × 4 updates/s) the throughput easily
 * exceeds the broker's 1h / 100MB-per-partition default budget. Per-topic retention is set tight
 * so the broker keeps only the recent window required by downstream consumers.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic cryptoScoutRawTopic(
            @Value("${crypto.kafka.topic.scout-raw}") String name,
            @Value("${crypto.kafka.topic.scout-raw-partitions:3}") int partitions,
            @Value("${crypto.kafka.topic.scout-raw-retention-ms:300000}") long retentionMs,
            @Value("${crypto.kafka.topic.scout-raw-retention-bytes:52428800}") long retentionBytes,
            @Value("${crypto.kafka.topic.scout-raw-segment-bytes:5242880}") long segmentBytes,
            @Value("${crypto.kafka.topic.scout-raw-segment-ms:60000}") long segmentMs) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(1)
                .configs(Map.of(
                        TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE,
                        TopicConfig.RETENTION_MS_CONFIG, Long.toString(retentionMs),
                        TopicConfig.RETENTION_BYTES_CONFIG, Long.toString(retentionBytes),
                        TopicConfig.SEGMENT_BYTES_CONFIG, Long.toString(segmentBytes),
                        TopicConfig.SEGMENT_MS_CONFIG, Long.toString(segmentMs)
                ))
                .build();
    }

}
