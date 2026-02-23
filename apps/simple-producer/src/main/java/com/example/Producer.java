package com.example;

import com.google.common.io.Resources;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class Producer {

    private static final String[] GLOBAL_EVENTS = {
            "maintenance_begin", "maintenance_end", "plan_removed", "plan_added", "sale_begin", "sale_end"
    };

    public static void main(String[] args) throws IOException {
        Properties properties = loadProperties();
        String userTopic = envOrDefault("PRODUCER_TOPIC", "user-events");
        String globalTopic = envOrDefault("GLOBAL_TOPIC", "global-events");
        long messageCount = envAsLong("MESSAGE_COUNT", 1_000L);
        int globalEvery = (int) envAsLong("GLOBAL_EVERY", 100L);
        int flushEvery = (int) envAsLong("FLUSH_EVERY", 1000L);
        int payloadSize = (int) envAsLong("PAYLOAD_SIZE", 256L);
        long reportIntervalMs = envAsLong("REPORT_INTERVAL_MS", 5000L);
        String payloadFiller = buildPayloadFiller(payloadSize);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            StatsTracker tracker = new StatsTracker(reportIntervalMs);
            for (long i = 0; i < messageCount; i++) {
                long producedAt = System.currentTimeMillis();
                String key = "user_id_" + i;
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        userTopic,
                        key,
                        buildPayload("user", key, i, producedAt, payloadFiller)
                );
                long start = System.nanoTime();
                producer.send(record, (metadata, exception) -> tracker.onCompletion(start, exception));

                if (globalEvery > 0 && i % globalEvery == 0) {
                    String eventKey = GLOBAL_EVENTS[ThreadLocalRandom.current().nextInt(GLOBAL_EVENTS.length)] + '_' + producedAt;
                    ProducerRecord<String, String> globalRecord = new ProducerRecord<>(
                            globalTopic,
                            eventKey,
                            buildPayload("global", eventKey, i, producedAt, payloadFiller)
                    );
                    long globalStart = System.nanoTime();
                    producer.send(globalRecord, (metadata, exception) -> tracker.onCompletion(globalStart, exception));
                }

                if (flushEvery > 0 && i % flushEvery == 0) {
                    producer.flush();
                }

                tracker.maybeReport();
            }
            producer.flush();
            tracker.logSummary();
        }
    }

    private static Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream props = Resources.getResource("producer.properties").openStream()) {
            properties.load(props);
        }
        overrideIfSet(properties, "BOOTSTRAP_SERVERS", "bootstrap.servers");
        overrideIfSet(properties, "ACKS", "acks");
        overrideIfSet(properties, "LINGER_MS", "linger.ms");
        overrideIfSet(properties, "BATCH_SIZE", "batch.size");
        overrideIfSet(properties, "COMPRESSION_TYPE", "compression.type");
        overrideIfSet(properties, "CLIENT_ID", "client.id");
        return properties;
    }

    private static void overrideIfSet(Properties props, String envKey, String propKey) {
        String value = System.getenv(envKey);
        if (value != null && !value.isBlank()) {
            props.setProperty(propKey, value);
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

    private static String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private static String buildPayload(String type, String key, long sequence, long producedAt, String filler) {
        return new StringBuilder()
                .append('{')
                .append("\"type\":\"").append(type).append('\"')
                .append(',')
                .append("\"key\":\"").append(key).append('\"')
                .append(',')
                .append("\"sequence\":").append(sequence)
                .append(',')
                .append("\"producedAt\":").append(producedAt)
                .append(',')
                .append("\"payload\":\"").append(filler).append('\"')
                .append('}')
                .toString();
    }

    private static String buildPayloadFiller(int payloadSize) {
        if (payloadSize <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(payloadSize);
        while (builder.length() < payloadSize) {
            builder.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
        }
        if (builder.length() > payloadSize) {
            return builder.substring(0, payloadSize);
        }
        return builder.toString();
    }

    private static final class StatsTracker {
        private final LongAdder sent = new LongAdder();
        private final LongAdder errors = new LongAdder();
        private final LongAdder latencyMicros = new LongAdder();
        private final AtomicLong lastReportCount = new AtomicLong();
        private final AtomicLong lastReportNanos = new AtomicLong(System.nanoTime());
        private final long reportIntervalNanos;

        private StatsTracker(long reportIntervalMs) {
            this.reportIntervalNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(1, reportIntervalMs));
        }

        private void onCompletion(long startNanos, Exception exception) {
            if (exception != null) {
                errors.increment();
                return;
            }
            sent.increment();
            long tookMicros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startNanos);
            latencyMicros.add(tookMicros);
        }

        private void maybeReport() {
            long now = System.nanoTime();
            long last = lastReportNanos.get();
            if (now - last < reportIntervalNanos) {
                return;
            }
            if (!lastReportNanos.compareAndSet(last, now)) {
                return;
            }
            long totalSent = sent.sum();
            long prevSent = lastReportCount.getAndSet(totalSent);
            long delta = totalSent - prevSent;
            double seconds = (now - last) / 1_000_000_000.0;
            double rate = seconds > 0 ? delta / seconds : 0;
            long totalErrors = errors.sum();
            double avgLatencyMs = totalSent == 0 ? 0 : (latencyMicros.sum() / (double) totalSent) / 1000.0;
            System.out.printf("[producer] sent=%d errors=%d avgLatencyMs=%.3f recentRate=%.1f msg/s%n",
                    totalSent,
                    totalErrors,
                    avgLatencyMs,
                    rate);
        }

        private void logSummary() {
            System.out.printf("[producer] completed run - sent=%d errors=%d avgLatencyMs=%.3f%n",
                    sent.sum(),
                    errors.sum(),
                    sent.sum() == 0 ? 0 : (latencyMicros.sum() / (double) sent.sum()) / 1000.0);
        }
    }
}
