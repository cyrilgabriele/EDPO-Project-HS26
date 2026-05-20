package ch.unisg.cryptoflow.portfolio.streams;

import ch.unisg.cryptoflow.events.CryptoPriceUpdatedEvent;
import ch.unisg.cryptoflow.events.OrderApprovedEvent;
import ch.unisg.cryptoflow.events.avro.PortfolioValue;
import ch.unisg.cryptoflow.events.avro.PositionValue;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioValuationTopologyTests {

    private static final String ORDER_TOPIC = "transaction.order.approved";
    private static final String PRICE_TOPIC = "crypto.price.raw";
    private static final String OUTPUT_TOPIC = "portfolio.value.updated";
    private static final String SCHEMA_REGISTRY_SCOPE = "scope-04-test";
    private static final String SCHEMA_REGISTRY_URL = "mock://" + SCHEMA_REGISTRY_SCOPE;

    private TopologyTestDriver driver;
    private TestInputTopic<String, OrderApprovedEvent> orderInput;
    private TestInputTopic<String, CryptoPriceUpdatedEvent> priceInput;
    private TestOutputTopic<String, PortfolioValue> portfolioOutput;
    private SpecificAvroSerde<PortfolioValue> portfolioValueSerde;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "scope-04-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY_URL);

        StreamsBuilder builder = new StreamsBuilder();
        PortfolioValuationStreamConfig.buildTopology(
                builder,
                ORDER_TOPIC,
                PRICE_TOPIC,
                OUTPUT_TOPIC,
                Map.of(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY_URL));
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        orderInput = driver.createInputTopic(ORDER_TOPIC, new StringSerializer(),
                new JsonSerializer<>());
        priceInput = driver.createInputTopic(PRICE_TOPIC, new StringSerializer(),
                new JsonSerializer<>());

        portfolioValueSerde = new SpecificAvroSerde<>();
        portfolioValueSerde.configure(
                Map.of(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY_URL),
                false);
        portfolioOutput = driver.createOutputTopic(OUTPUT_TOPIC, new StringDeserializer(),
                portfolioValueSerde.deserializer());
    }

    @AfterEach
    void tearDown() {
        if (driver != null) driver.close();
        if (portfolioValueSerde != null) portfolioValueSerde.close();
        MockSchemaRegistry.dropScope(SCHEMA_REGISTRY_SCOPE);
    }

    @Test
    void emitsPortfolioValueWhenOrderArrivesAfterPrice() {
        priceInput.pipeInput("BTCUSDT", price("BTCUSDT", "30000"));
        orderInput.pipeInput("u-1", buy("tx-1", "u-1", "BTCUSDT", "0.5", "29500"));

        PortfolioValue latest = lastValue(portfolioOutput.readKeyValuesToList());
        assertThat(latest.getUserId()).isEqualTo("u-1");
        assertThat(latest.getBreakdown()).hasSize(1);
        assertThat(latest.getBreakdown().get(0).getSymbol()).isEqualTo("BTCUSDT");
        assertThat(latest.getBreakdown().get(0).getQuantity()).isEqualByComparingTo("0.5");
        assertThat(latest.getBreakdown().get(0).getValueUsdt()).isEqualByComparingTo("15000");
        assertThat(latest.getTotalUsdt()).isEqualByComparingTo("15000");
    }

    @Test
    void recomputesWhenPriceUpdates() {
        priceInput.pipeInput("BTCUSDT", price("BTCUSDT", "30000"));
        orderInput.pipeInput("u-1", buy("tx-1", "u-1", "BTCUSDT", "0.5", "29500"));
        portfolioOutput.readKeyValuesToList();

        priceInput.pipeInput("BTCUSDT", price("BTCUSDT", "31000"));

        PortfolioValue latest = lastValue(portfolioOutput.readKeyValuesToList());
        assertThat(latest.getTotalUsdt()).isEqualByComparingTo("15500");
    }

    @Test
    void aggregatesAcrossMultipleSymbolsPerUser() {
        priceInput.pipeInput("BTCUSDT", price("BTCUSDT", "30000"));
        priceInput.pipeInput("ETHUSDT", price("ETHUSDT", "2000"));
        orderInput.pipeInput("u-1", buy("tx-1", "u-1", "BTCUSDT", "0.5", "29500"));
        orderInput.pipeInput("u-1", buy("tx-2", "u-1", "ETHUSDT", "10", "1990"));

        PortfolioValue latest = lastValue(portfolioOutput.readKeyValuesToList());
        assertThat(latest.getTotalUsdt()).isEqualByComparingTo("35000");
        assertThat(latest.getBreakdown()).extracting(PositionValue::getSymbol)
                .containsExactly("BTCUSDT", "ETHUSDT");
    }

    @Test
    void portfolioValueStoreIsQueryable() {
        priceInput.pipeInput("BTCUSDT", price("BTCUSDT", "30000"));
        orderInput.pipeInput("u-1", buy("tx-1", "u-1", "BTCUSDT", "0.5", "29500"));

        KeyValueStore<String, PortfolioValue> store = driver.getKeyValueStore(
                PortfolioValuationStreamConfig.PORTFOLIO_VALUE_STORE);
        PortfolioValue stored = store.get("u-1");
        assertThat(stored).isNotNull();
        assertThat(stored.getTotalUsdt()).isEqualByComparingTo("15000");
    }

    @Test
    void secondBuyAddsToExistingHoldings() {
        priceInput.pipeInput("BTCUSDT", price("BTCUSDT", "30000"));
        orderInput.pipeInput("u-1", buy("tx-1", "u-1", "BTCUSDT", "0.5", "29500"));
        orderInput.pipeInput("u-1", buy("tx-2", "u-1", "BTCUSDT", "0.25", "30500"));

        PortfolioValue latest = lastValue(portfolioOutput.readKeyValuesToList());
        assertThat(latest.getBreakdown()).hasSize(1);
        assertThat(latest.getBreakdown().get(0).getQuantity()).isEqualByComparingTo("0.75");
        assertThat(latest.getTotalUsdt()).isEqualByComparingTo("22500");
    }

    private static OrderApprovedEvent buy(String txId, String userId, String symbol,
                                          String amount, String price) {
        return new OrderApprovedEvent(txId, userId, symbol,
                new BigDecimal(amount), new BigDecimal(price),
                Instant.parse("2026-05-20T10:00:00Z"));
    }

    private static CryptoPriceUpdatedEvent price(String symbol, String price) {
        return new CryptoPriceUpdatedEvent("evt-" + symbol, symbol,
                new BigDecimal(price), Instant.parse("2026-05-20T10:00:00Z"));
    }

    private static <V> V lastValue(List<? extends org.apache.kafka.streams.KeyValue<String, V>> records) {
        assertThat(records).isNotEmpty();
        return records.get(records.size() - 1).value;
    }
}
