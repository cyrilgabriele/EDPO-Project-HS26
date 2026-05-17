package ch.unisg.cryptoflow.marketdata.streams;

import ch.unisg.cryptoflow.events.CryptoPriceUpdatedEvent;
import ch.unisg.cryptoflow.events.avro.Ohlc;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.Suppressed.BufferConfig;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Scope-05 OHLC topology (event-processes/05-ohlc-candles.md, ADR-0031).
 *
 * <p>One Kafka Streams app inside market-data-service that consumes the existing
 * JSON {@code crypto.price.raw} stream, groups by symbol, runs three parallel
 * tumbling-window aggregations (1m / 5m / 1h), suppresses each until window
 * close, and emits one Avro {@link Ohlc} per (symbol, window) to
 * {@code crypto.ohlc.{interval}}.
 *
 * <p>Source tracer-phase note: ADR-0031 names {@code crypto.price.clean} as the
 * canonical source. Scope 02 has not shipped yet, so this topology reads
 * {@code crypto.price.raw} directly. The swap is a one-line config change.
 */
@Configuration
@EnableKafkaStreams
public class OhlcStreamConfig {

    @Value("${ohlc.application-id}")
    private String applicationId;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Value("${crypto.kafka.topic.price-raw}")
    private String priceRawTopic;

    @Value("${crypto.kafka.topic.ohlc-1m}")
    private String ohlc1mTopic;

    @Value("${crypto.kafka.topic.ohlc-5m}")
    private String ohlc5mTopic;

    @Value("${crypto.kafka.topic.ohlc-1h}")
    private String ohlc1hTopic;

    @Value("${ohlc.windows.one-minute.size}")
    private Duration oneMinuteSize;

    @Value("${ohlc.windows.one-minute.grace}")
    private Duration oneMinuteGrace;

    @Value("${ohlc.windows.five-minutes.size}")
    private Duration fiveMinutesSize;

    @Value("${ohlc.windows.five-minutes.grace}")
    private Duration fiveMinutesGrace;

    @Value("${ohlc.windows.one-hour.size}")
    private Duration oneHourSize;

    @Value("${ohlc.windows.one-hour.grace}")
    private Duration oneHourGrace;

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kStreamsConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        props.put(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG, PriceTickTimestampExtractor.class.getName());
        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                org.apache.kafka.streams.errors.LogAndContinueExceptionHandler.class.getName());
        // earliest so a fresh app rebuilds bars from the start of crypto.price.raw on first run
        props.put(StreamsConfig.consumerPrefix(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG), "earliest");
        return new KafkaStreamsConfiguration(props);
    }

    @Bean
    public Serde<CryptoPriceUpdatedEvent> priceTickSerde() {
        JsonSerde<CryptoPriceUpdatedEvent> serde = new JsonSerde<>(CryptoPriceUpdatedEvent.class);
        serde.deserializer().addTrustedPackages("ch.unisg.cryptoflow.events");
        serde.deserializer().setUseTypeHeaders(false);
        return serde;
    }

    @Bean
    public Serde<Ohlc> ohlcSerde() {
        SpecificAvroSerde<Ohlc> serde = new SpecificAvroSerde<>();
        serde.configure(
                Map.of(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl),
                false);
        return serde;
    }

    @Bean
    public KStream<String, CryptoPriceUpdatedEvent> ohlcTopology(
            StreamsBuilder builder,
            Serde<CryptoPriceUpdatedEvent> priceTickSerde,
            Serde<Ohlc> ohlcSerde) {

        KStream<String, CryptoPriceUpdatedEvent> ticks = builder.stream(
                priceRawTopic,
                Consumed.with(Serdes.String(), priceTickSerde));

        ticks.peek((k, v) -> {}); // keep the source as a single branch for the three siblings below

        buildIntervalPipeline(ticks, oneMinuteSize, oneMinuteGrace, 60, "ohlc-1m-store", ohlc1mTopic, priceTickSerde, ohlcSerde);
        buildIntervalPipeline(ticks, fiveMinutesSize, fiveMinutesGrace, 300, "ohlc-5m-store", ohlc5mTopic, priceTickSerde, ohlcSerde);
        buildIntervalPipeline(ticks, oneHourSize, oneHourGrace, 3600, "ohlc-1h-store", ohlc1hTopic, priceTickSerde, ohlcSerde);

        return ticks;
    }

    private void buildIntervalPipeline(
            KStream<String, CryptoPriceUpdatedEvent> ticks,
            Duration size,
            Duration grace,
            int intervalSec,
            String storeName,
            String outputTopic,
            Serde<CryptoPriceUpdatedEvent> priceTickSerde,
            Serde<Ohlc> ohlcSerde) {

        ticks
                .groupByKey(Grouped.with(Serdes.String(), priceTickSerde))
                .windowedBy(TimeWindows.ofSizeAndGrace(size, grace))
                .aggregate(
                        OhlcAggregator::empty,
                        (symbol, tick, bar) -> OhlcAggregator.update(symbol, tick, bar),
                        Materialized.<String, Ohlc>as(
                                org.apache.kafka.streams.state.Stores.persistentWindowStore(
                                        storeName,
                                        Duration.ofMillis(size.toMillis() * 2L + grace.toMillis()),
                                        size,
                                        false))
                                .withKeySerde(Serdes.String())
                                .withValueSerde(ohlcSerde))
                .suppress(Suppressed.untilWindowCloses(BufferConfig.unbounded()))
                .toStream()
                .map((windowedKey, bar) -> {
                    bar.setSymbol(windowedKey.key());
                    bar.setIntervalSec(intervalSec);
                    bar.setWindowStart(Instant.ofEpochMilli(windowedKey.window().start()));
                    bar.setWindowEnd(Instant.ofEpochMilli(windowedKey.window().end()));
                    return KeyValue.pair(windowedKey.key(), bar);
                })
                .to(outputTopic, Produced.with(Serdes.String(), ohlcSerde));
    }
}
