package com.example;

import com.google.common.io.Resources;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class Consumer {
    public static void main(String[] args) throws IOException {
        Properties properties = loadProperties();
        List<String> topics = parseTopics();
        long processingDelayMs = envAsLong("PROCESSING_DELAY_MS", 0L);
        long processingJitterMs = envAsLong("PROCESSING_JITTER_MS", 0L);
        long reportIntervalMs = envAsLong("REPORT_INTERVAL_MS", 5000L);
        boolean printRecords = envFlag("PRINT_RECORDS", false);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(topics);
            StatsTracker tracker = new StatsTracker(reportIntervalMs);
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));

                for (ConsumerRecord<String, String> record : records) {
                    applyDelay(processingDelayMs, processingJitterMs);
                    long producedAt = extractProducedAt(record.value());
                    long latencyMs = producedAt > 0 ? System.currentTimeMillis() - producedAt : -1;
                    tracker.record(record.topic(), latencyMs, record.partition(), record.offset());
                    if (printRecords) {
                        System.out.printf(Locale.ROOT,
                                "[consumer] topic=%s partition=%d offset=%d key=%s latencyMs=%d%n",
                                record.topic(), record.partition(), record.offset(), record.key(), latencyMs);
                    }
                }
                tracker.maybeReport();
            }
        }
    }

    private static Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream props = Resources.getResource("consumer.properties").openStream()) {
            properties.load(props);
        }
        overrideIfSet(properties, "BOOTSTRAP_SERVERS", "bootstrap.servers");
        overrideIfSet(properties, "GROUP_ID", "group.id");
        overrideIfSet(properties, "AUTO_OFFSET_RESET", "auto.offset.reset");
        overrideIfSet(properties, "MAX_POLL_RECORDS", "max.poll.records");
        overrideIfSet(properties, "MAX_POLL_INTERVAL_MS", "max.poll.interval.ms");
        return properties;
    }

    private static void overrideIfSet(Properties props, String envKey, String propKey) {
        String value = System.getenv(envKey);
        if (value != null && !value.isBlank()) {
            props.setProperty(propKey, value);
        }
    }

    private static List<String> parseTopics() {
        String value = System.getenv("CONSUMER_TOPICS");
        if (value == null || value.isBlank()) {
            return Arrays.asList("user-events", "global-events");
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static void applyDelay(long baseDelayMs, long jitterMs) {
        if (baseDelayMs <= 0 && jitterMs <= 0) {
            return;
        }
        long delay = baseDelayMs;
        if (jitterMs > 0) {
            delay += ThreadLocalRandom.current().nextLong(0, jitterMs);
        }
        if (delay <= 0) {
            return;
        }
        try {
            TimeUnit.MILLISECONDS.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static long extractProducedAt(String payload) {
        if (payload == null) {
            return -1;
        }
        int idx = payload.indexOf("\"producedAt\":");
        if (idx == -1) {
            return -1;
        }
        int start = idx + "\"producedAt\":".length();
        int end = start;
        while (end < payload.length() && Character.isDigit(payload.charAt(end))) {
            end++;
        }
        if (end == start) {
            return -1;
        }
        try {
            return Long.parseLong(payload.substring(start, end));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static long envAsLong(String key, long defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static boolean envFlag(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    private static final class StatsTracker {
        private final long reportIntervalNanos;
        private final java.util.Map<String, TopicStats> stats = new java.util.HashMap<>();
        private long lastReport = System.nanoTime();

        private StatsTracker(long reportIntervalMs) {
            this.reportIntervalNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(1, reportIntervalMs));
        }

        private void record(String topic, long latencyMs, int partition, long offset) {
            TopicStats topicStats = stats.computeIfAbsent(topic, t -> new TopicStats());
            topicStats.count++;
            topicStats.lastPartition = partition;
            topicStats.lastOffset = offset;
            if (latencyMs >= 0) {
                topicStats.totalLatency += latencyMs;
                topicStats.maxLatency = Math.max(topicStats.maxLatency, latencyMs);
            }
        }

        private void maybeReport() {
            long now = System.nanoTime();
            if (now - lastReport < reportIntervalNanos) {
                return;
            }
            lastReport = now;
            stats.forEach((topic, topicStats) -> {
                double avgLatency = topicStats.count == 0 ? 0 : (double) topicStats.totalLatency / topicStats.count;
                System.out.printf(Locale.ROOT,
                        "[consumer] topic=%s count=%d avgLatencyMs=%.2f maxLatencyMs=%d lastPartition=%d lastOffset=%d%n",
                        topic,
                        topicStats.count,
                        avgLatency,
                        topicStats.maxLatency,
                        topicStats.lastPartition,
                        topicStats.lastOffset);
            });
        }

        private static final class TopicStats {
            private long count = 0;
            private long totalLatency = 0;
            private long maxLatency = 0;
            private int lastPartition = -1;
            private long lastOffset = -1;
        }
    }
}
