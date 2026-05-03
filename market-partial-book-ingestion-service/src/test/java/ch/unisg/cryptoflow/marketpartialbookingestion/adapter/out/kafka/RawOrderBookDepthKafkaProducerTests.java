package ch.unisg.cryptoflow.marketpartialbookingestion.adapter.out.kafka;

import ch.unisg.cryptoflow.events.RawOrderBookDepthEvent;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.mock.MockProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RawOrderBookDepthKafkaProducerTests {

    @Test
    void publishesRawEventKeyedBySymbolToConfiguredTopic() {
        MockProducer<String, RawOrderBookDepthEvent> mockProducer = new MockProducer<>(
                true,
                new StringSerializer(),
                new JsonSerializer<>());
        KafkaTemplate<String, RawOrderBookDepthEvent> kafkaTemplate =
                new KafkaTemplate<>(new MockProducerFactory<>(() -> mockProducer));
        RawOrderBookDepthKafkaProducer producer = new RawOrderBookDepthKafkaProducer(kafkaTemplate, "crypto.scout.raw");

        RawOrderBookDepthEvent event = new RawOrderBookDepthEvent(
                UUID.randomUUID().toString(),
                "ETHUSDT",
                Instant.parse("2026-05-03T10:15:00Z"),
                Instant.parse("2026-05-03T10:15:01Z"),
                1L,
                2L,
                0L,
                List.of(),
                List.of(),
                Instant.parse("2026-05-03T10:15:02Z"));

        producer.publish(event);

        assertThat(mockProducer.history()).hasSize(1);
        var record = mockProducer.history().getFirst();
        assertThat(record.topic()).isEqualTo("crypto.scout.raw");
        assertThat(record.key()).isEqualTo("ETHUSDT");
        assertThat(record.value()).isEqualTo(event);
    }
}
