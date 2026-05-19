package ch.unisg.cryptoflow.portfolio.streams;

import ch.unisg.cryptoflow.events.CryptoPriceUpdatedEvent;
import ch.unisg.cryptoflow.events.OrderApprovedEvent;
import ch.unisg.cryptoflow.events.avro.PortfolioValue;
import ch.unisg.cryptoflow.events.avro.PositionValue;
import ch.unisg.cryptoflow.events.avro.SpecificAvroSerde;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Scope-04 portfolio valuation topology (event-processes/04-portfolio-valuation.md,
 * ADR-0034).
 *
 * <p>Lives inside portfolio-service so the interactive-query endpoint can read
 * the materialised state store directly. Runs as a separate consumer group
 * ({@code portfolio-service-valuation}) from the existing
 * {@code @KafkaListener} consumers.
 *
 * <p>Patterns demonstrated:
 * <ul>
 *   <li>State-and-Stream-Table duality — both inputs become KTables.</li>
 *   <li>FK Table-Table Join — holdings (key {@code userId|symbol}) × prices
 *       (key {@code symbol}) on the symbol component carried in the holdings
 *       value (Kafka Streams 3.x FK extractor is value-only).</li>
 *   <li>Multiphase Repartitioning — {@code KTable.groupBy(userId)} triggers
 *       an internal repartition before the per-user aggregate.</li>
 *   <li>Processing with Local State — materialised
 *       {@code portfolio-value-store} backs the IQ endpoint.</li>
 *   <li>Interactive Queries — see {@link PortfolioValueStoreReader}.</li>
 * </ul>
 */
@Configuration
@EnableKafkaStreams
public class PortfolioValuationStreamConfig {

    public static final String HOLDINGS_STORE = "portfolio-valuation-holdings-store";
    public static final String PRICES_STORE = "portfolio-valuation-prices-store";
    public static final String POSITION_VALUE_STORE = "portfolio-valuation-position-value-store";
    public static final String PORTFOLIO_VALUE_STORE = "portfolio-value-store";

    @Value("${portfolio.valuation.application-id}")
    private String applicationId;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Value("${crypto.kafka.topic.order-approved}")
    private String orderApprovedTopic;

    @Value("${crypto.kafka.topic.price-raw}")
    private String priceRawTopic;

    @Value("${crypto.kafka.topic.portfolio-value}")
    private String portfolioValueTopic;

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kStreamsConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.AT_LEAST_ONCE);
        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                org.apache.kafka.streams.errors.LogAndContinueExceptionHandler.class.getName());
        props.put(StreamsConfig.consumerPrefix(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG), "earliest");
        return new KafkaStreamsConfiguration(props);
    }

    @Bean
    public KStream<String, PortfolioValue> portfolioValuationTopology(StreamsBuilder builder) {
        return buildTopology(
                builder,
                orderApprovedTopic,
                priceRawTopic,
                portfolioValueTopic,
                Map.of(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl));
    }

    /**
     * Holdings KTable value. The symbol travels with the quantity so the
     * FK-join extractor (value-only in Kafka Streams 3.x) can pull the FK
     * without needing to decode the composite key.
     */
    public record Holding(String symbol, BigDecimal quantity) {}

    static KStream<String, PortfolioValue> buildTopology(
            StreamsBuilder builder,
            String orderApprovedTopic,
            String priceRawTopic,
            String portfolioValueTopic,
            Map<String, ?> serdeConfig) {

        Serde<String> keySerde = Serdes.String();

        JsonSerde<OrderApprovedEvent> orderSerde = new JsonSerde<>(OrderApprovedEvent.class);
        orderSerde.deserializer().addTrustedPackages("ch.unisg.cryptoflow.events");
        orderSerde.deserializer().setUseTypeHeaders(false);

        JsonSerde<CryptoPriceUpdatedEvent> priceSerde = new JsonSerde<>(CryptoPriceUpdatedEvent.class);
        priceSerde.deserializer().addTrustedPackages("ch.unisg.cryptoflow.events");
        priceSerde.deserializer().setUseTypeHeaders(false);

        JsonSerde<BigDecimal> bigDecimalSerde = new JsonSerde<>(BigDecimal.class);
        bigDecimalSerde.deserializer().setUseTypeHeaders(false);

        JsonSerde<Holding> holdingSerde = new JsonSerde<>(Holding.class);
        holdingSerde.deserializer().addTrustedPackages(
                "ch.unisg.cryptoflow.portfolio.streams");
        holdingSerde.deserializer().setUseTypeHeaders(false);

        // PositionValue is an Avro-generated record; JsonSerde + Jackson cannot
        // serialise it because Jackson tries to walk SpecificData.conversions →
        // DecimalConversion.getRecommendedSchema(), which throws (the bytes/
        // decimal logical type has no recommended schema without a scale).
        // The local binary SpecificAvroSerde sidesteps Jackson entirely.
        SpecificAvroSerde<PositionValue> positionValueSerde =
                new SpecificAvroSerde<>(PositionValue.class, PositionValue.getClassSchema());
        positionValueSerde.configure(serdeConfig, false);

        SpecificAvroSerde<PortfolioValue> portfolioValueSerde =
                new SpecificAvroSerde<>(PortfolioValue.class, PortfolioValue.getClassSchema());
        portfolioValueSerde.configure(serdeConfig, false);

        // ── Holdings KTable: signed-sum fold per (userId, symbol) ────────────
        KTable<String, Holding> holdings = builder
                .stream(orderApprovedTopic, Consumed.with(keySerde, orderSerde))
                .filter((k, ev) -> ev != null && ev.userId() != null && ev.symbol() != null)
                .map((k, ev) -> KeyValue.pair(
                        UserSymbolKey.encode(ev.userId(), ev.symbol()),
                        new Holding(ev.symbol().toUpperCase(java.util.Locale.ROOT),
                                SignedQuantity.of(ev))))
                .groupByKey(Grouped.with(keySerde, holdingSerde))
                .aggregate(
                        () -> new Holding("", BigDecimal.ZERO),
                        (key, delta, current) -> new Holding(
                                delta.symbol(),
                                current.quantity().add(delta.quantity())),
                        Materialized.<String, Holding, KeyValueStore<Bytes, byte[]>>as(HOLDINGS_STORE)
                                .withKeySerde(keySerde)
                                .withValueSerde(holdingSerde));

        // ── Prices KTable: latest price per symbol ──────────────────────────
        KTable<String, BigDecimal> prices = builder
                .stream(priceRawTopic, Consumed.with(keySerde, priceSerde))
                .filter((k, ev) -> ev != null && ev.symbol() != null && ev.price() != null)
                .map((k, ev) -> KeyValue.pair(ev.symbol().toUpperCase(java.util.Locale.ROOT), ev.price()))
                .toTable(Materialized.<String, BigDecimal, KeyValueStore<Bytes, byte[]>>as(PRICES_STORE)
                        .withKeySerde(keySerde)
                        .withValueSerde(bigDecimalSerde));

        // ── FK Table-Table Join: holdings × prices on symbol ────────────────
        KTable<String, PositionValue> positionValues = holdings.join(
                prices,
                Holding::symbol,
                (holding, price) -> PositionValue.newBuilder()
                        .setSymbol(holding.symbol())
                        .setQuantity(holding.quantity())
                        .setValueUsdt(holding.quantity().multiply(price))
                        .build(),
                Materialized.<String, PositionValue, KeyValueStore<Bytes, byte[]>>as(POSITION_VALUE_STORE)
                        .withKeySerde(keySerde)
                        .withValueSerde(positionValueSerde));

        // ── Multiphase repartition: groupBy(userId) + aggregate ─────────────
        KTable<String, PortfolioValue> portfolioValues = positionValues
                .groupBy(
                        (compositeKey, pv) -> KeyValue.pair(
                                UserSymbolKey.decode(compositeKey).userId(), pv),
                        Grouped.with(keySerde, positionValueSerde))
                .aggregate(
                        () -> PortfolioValueAggregator.empty(""),
                        (userId, pv, agg) -> PortfolioValueAggregator.add(userId, pv, agg, Instant.now()),
                        (userId, pv, agg) -> PortfolioValueAggregator.subtract(userId, pv, agg, Instant.now()),
                        Materialized.<String, PortfolioValue, KeyValueStore<Bytes, byte[]>>as(PORTFOLIO_VALUE_STORE)
                                .withKeySerde(keySerde)
                                .withValueSerde(portfolioValueSerde));

        KStream<String, PortfolioValue> output = portfolioValues
                .toStream()
                .filter((userId, pv) -> pv != null);

        output.to(portfolioValueTopic, Produced.with(keySerde, portfolioValueSerde));
        return output;
    }
}
