package ch.unisg.cryptoflow.marketscout.adapter.in.kafka;

import ch.unisg.cryptoflow.events.OrderBookLevel;
import ch.unisg.cryptoflow.events.RawOrderBookDepthEvent;
import ch.unisg.cryptoflow.marketscout.avro.AskOpportunity;
import ch.unisg.cryptoflow.marketscout.avro.AskQuote;
import ch.unisg.cryptoflow.marketscout.avro.ScoutWindowSummary;
import ch.unisg.cryptoflow.marketscout.config.serde.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarketScoutTopologyTests {

    private static final String RAW_TOPIC = "crypto.scout.raw";
    private static final String ASK_QUOTE_TOPIC = "crypto.scout.ask-quotes";
    private static final String ASK_OPPORTUNITY_TOPIC = "crypto.scout.ask-opportunities";
    private static final String SUMMARY_TOPIC = "crypto.scout.window-summary";
    private static final Instant PROCESSED_AT = Instant.parse("2026-05-03T12:00:00Z");

    private final Serde<String> keySerde = Serdes.String();
    private final JsonSerde<RawOrderBookDepthEvent> rawSerde = new JsonSerde<>(RawOrderBookDepthEvent.class);
    private final SpecificAvroSerde<AskQuote> askQuoteSerde =
            new SpecificAvroSerde<>(AskQuote.class, AskQuote.getClassSchema());
    private final SpecificAvroSerde<AskOpportunity> askOpportunitySerde =
            new SpecificAvroSerde<>(AskOpportunity.class, AskOpportunity.getClassSchema());
    private final SpecificAvroSerde<ScoutWindowSummary> summarySerde =
            new SpecificAvroSerde<>(ScoutWindowSummary.class, ScoutWindowSummary.getClassSchema());

    @Test
    void rawEventWithBidsAndAsksBecomesAskOnlyQuotes() {
        try (TopologyTestDriver driver = testDriver(new BigDecimal("100.00"))) {
            TestInputTopic<String, RawOrderBookDepthEvent> input = inputTopic(driver);
            TestOutputTopic<String, AskQuote> quotes = askQuoteOutput(driver);

            input.pipeInput("BTCUSDT", rawEvent(
                    "BTCUSDT",
                    List.of(new OrderBookLevel(new BigDecimal("90.00"), new BigDecimal("3.00"))),
                    List.of(
                            new OrderBookLevel(new BigDecimal("101.00"), new BigDecimal("1.25")),
                            new OrderBookLevel(new BigDecimal("102.00"), new BigDecimal("2.50"))),
                    Instant.parse("2026-05-03T10:15:05Z")));

            KeyValue<String, AskQuote> firstQuote = quotes.readKeyValue();
            KeyValue<String, AskQuote> secondQuote = quotes.readKeyValue();

            assertThat(firstQuote.value.getPrice()).isEqualByComparingTo("101.00");
            assertThat(firstQuote.value.getQuantity()).isEqualByComparingTo("1.25");
            assertThat(firstQuote.value.getBestAskPrice()).isEqualByComparingTo("101.00");
            assertThat(firstQuote.value.getBestAskQuantity()).isEqualByComparingTo("1.25");
            assertThat(firstQuote.value.getAskNotional()).isEqualByComparingTo("126.25");

            assertThat(secondQuote.value.getPrice()).isEqualByComparingTo("102.00");
            assertThat(secondQuote.value.getQuantity()).isEqualByComparingTo("2.50");
            assertThat(secondQuote.value.getBestAskPrice()).isEqualByComparingTo("101.00");
            assertThat(secondQuote.value.getBestAskQuantity()).isEqualByComparingTo("1.25");
            assertThat(secondQuote.value.getAskNotional()).isEqualByComparingTo("255.00");
            assertThat(quotes.isEmpty()).isTrue();
        }
    }

    @Test
    void translatedQuotesKeepSymbolKeyAndScoutMetadata() {
        try (TopologyTestDriver driver = testDriver(new BigDecimal("200.00"))) {
            TestInputTopic<String, RawOrderBookDepthEvent> input = inputTopic(driver);
            TestOutputTopic<String, AskQuote> quotes = askQuoteOutput(driver);

            Instant transactionTime = Instant.parse("2026-05-03T10:15:05Z");
            input.pipeInput("ETHUSDT", rawEvent(
                    "ETHUSDT",
                    List.of(),
                    List.of(new OrderBookLevel(new BigDecimal("150.00"), new BigDecimal("0.75"))),
                    transactionTime));

            var translated = quotes.readKeyValue();

            assertThat(translated.key).isEqualTo("ETHUSDT");
            assertThat(translated.value.getSymbol()).isEqualTo("ETHUSDT");
            assertThat(translated.value.getQuantity()).isEqualByComparingTo("0.75");
            assertThat(translated.value.getBestAskPrice()).isEqualByComparingTo("150.00");
            assertThat(translated.value.getBestAskQuantity()).isEqualByComparingTo("0.75");
            assertThat(translated.value.getAskNotional()).isEqualByComparingTo("112.50");
            assertThat(translated.value.getTransactionTime()).isEqualTo(transactionTime);
            assertThat(translated.value.getSourceVenue()).isEqualTo("BINANCE_USD_M_FUTURES");
            assertThat(translated.value.getProcessedAt()).isEqualTo(PROCESSED_AT);
        }
    }

    @Test
    void thresholdKeepsOnlyQuotesAtOrBelowConfiguredPrice() {
        try (TopologyTestDriver driver = testDriver(new BigDecimal("100.00"))) {
            TestInputTopic<String, RawOrderBookDepthEvent> input = inputTopic(driver);
            TestOutputTopic<String, AskOpportunity> opportunities = askOpportunityOutput(driver);

            input.pipeInput("BTCUSDT", rawEvent(
                    "BTCUSDT",
                    List.of(),
                    List.of(
                            new OrderBookLevel(new BigDecimal("99.99"), new BigDecimal("1.00")),
                            new OrderBookLevel(new BigDecimal("100.00"), new BigDecimal("2.00")),
                            new OrderBookLevel(new BigDecimal("100.01"), new BigDecimal("3.00"))),
                    Instant.parse("2026-05-03T10:15:05Z")));

            AskOpportunity firstOpportunity = opportunities.readValue();
            AskOpportunity secondOpportunity = opportunities.readValue();

            assertThat(firstOpportunity.getPrice()).isEqualByComparingTo("99.99");
            assertThat(firstOpportunity.getQuantity()).isEqualByComparingTo("1.00");
            assertThat(firstOpportunity.getBestAskPrice()).isEqualByComparingTo("99.99");
            assertThat(firstOpportunity.getBestAskQuantity()).isEqualByComparingTo("1.00");
            assertThat(firstOpportunity.getAskNotional()).isEqualByComparingTo("99.99");

            assertThat(secondOpportunity.getPrice()).isEqualByComparingTo("100.00");
            assertThat(secondOpportunity.getQuantity()).isEqualByComparingTo("2.00");
            assertThat(secondOpportunity.getBestAskPrice()).isEqualByComparingTo("99.99");
            assertThat(secondOpportunity.getBestAskQuantity()).isEqualByComparingTo("1.00");
            assertThat(secondOpportunity.getAskNotional()).isEqualByComparingTo("200.00");
            assertThat(opportunities.isEmpty()).isTrue();
        }
    }

    @Test
    void windowedAggregateCountsOpportunitiesAndTracksMinAskPricePerSymbol() {
        try (TopologyTestDriver driver = testDriver(new BigDecimal("100.00"))) {
            TestInputTopic<String, RawOrderBookDepthEvent> input = inputTopic(driver);
            TestOutputTopic<String, ScoutWindowSummary> summaries = summaryOutput(driver);

            Instant transactionTime = Instant.parse("2026-05-03T10:15:05Z");
            input.pipeInput("BTCUSDT", rawEvent(
                    "BTCUSDT",
                    List.of(),
                    List.of(
                            new OrderBookLevel(new BigDecimal("99.50"), new BigDecimal("1.00")),
                            new OrderBookLevel(new BigDecimal("98.25"), new BigDecimal("2.00")),
                            new OrderBookLevel(new BigDecimal("101.00"), new BigDecimal("3.00"))),
                    transactionTime));

            ScoutWindowSummary firstSummary = summaries.readValue();
            ScoutWindowSummary secondSummary = summaries.readValue();

            assertThat(firstSummary.getOpportunityCount()).isEqualTo(1);
            assertThat(secondSummary.getSymbol()).isEqualTo("BTCUSDT");
            assertThat(secondSummary.getOpportunityCount()).isEqualTo(2);
            assertThat(secondSummary.getMinAskPrice()).isEqualByComparingTo("98.25");
            assertThat(secondSummary.getWindowStart()).isEqualTo(Instant.parse("2026-05-03T10:15:00Z"));
            assertThat(secondSummary.getWindowEnd()).isEqualTo(Instant.parse("2026-05-03T10:15:30Z"));
            assertThat(summaries.isEmpty()).isTrue();
        }
    }

    @Test
    void emptyOrMalformedAskListsAreIgnoredWithoutKillingStreamProcessing() {
        try (TopologyTestDriver driver = testDriver(new BigDecimal("100.00"))) {
            TestInputTopic<String, RawOrderBookDepthEvent> input = inputTopic(driver);
            TestOutputTopic<String, AskQuote> quotes = askQuoteOutput(driver);

            input.pipeInput("BTCUSDT", rawEvent(
                    "BTCUSDT",
                    List.of(new OrderBookLevel(new BigDecimal("90.00"), new BigDecimal("1.00"))),
                    List.of(),
                    Instant.parse("2026-05-03T10:15:05Z")));
            input.pipeInput("BTCUSDT", rawEvent(
                    "BTCUSDT",
                    List.of(),
                    Arrays.asList(
                            null,
                            new OrderBookLevel(null, new BigDecimal("1.00")),
                            new OrderBookLevel(new BigDecimal("98.50"), null),
                            new OrderBookLevel(new BigDecimal("99.00"), new BigDecimal("1.00"))),
                    Instant.parse("2026-05-03T10:15:06Z")));

            AskQuote validQuote = quotes.readValue();

            assertThat(validQuote.getPrice()).isEqualByComparingTo("99.00");
            assertThat(validQuote.getQuantity()).isEqualByComparingTo("1.00");
            assertThat(validQuote.getBestAskPrice()).isEqualByComparingTo("99.00");
            assertThat(validQuote.getBestAskQuantity()).isEqualByComparingTo("1.00");
            assertThat(validQuote.getAskNotional()).isEqualByComparingTo("99.00");
            assertThat(quotes.isEmpty()).isTrue();
        }
    }

    @Test
    void askNotionalIsRoundedToDecimalScaleWhenMultiplicationExceedsScale() {
        try (TopologyTestDriver driver = testDriver(new BigDecimal("200.00"))) {
            TestInputTopic<String, RawOrderBookDepthEvent> input = inputTopic(driver);
            TestOutputTopic<String, AskQuote> quotes = askQuoteOutput(driver);

            input.pipeInput("ETHUSDT", rawEvent(
                    "ETHUSDT",
                    List.of(),
                    List.of(new OrderBookLevel(
                            new BigDecimal("1.123456789123456789"),
                            new BigDecimal("2.123456789123456789"))),
                    Instant.parse("2026-05-03T10:15:05Z")));

            AskQuote quote = quotes.readValue();

            assertThat(quote.getAskNotional()).isEqualByComparingTo("2.385611946151044046");
            assertThat(quotes.isEmpty()).isTrue();
        }
    }

    private TopologyTestDriver testDriver(BigDecimal threshold) {
        rawSerde.noTypeInfo();
        StreamsBuilder builder = new StreamsBuilder();
        MarketScoutTopology.build(builder, new MarketScoutTopologyProperties(
                RAW_TOPIC,
                ASK_QUOTE_TOPIC,
                ASK_OPPORTUNITY_TOPIC,
                SUMMARY_TOPIC,
                threshold,
                Duration.ofSeconds(30),
                "BINANCE_USD_M_FUTURES"), Clock.fixed(PROCESSED_AT, ZoneOffset.UTC));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "market-scout-topology-test-" + UUID.randomUUID());
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        properties.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);

        return new TopologyTestDriver(builder.build(), properties);
    }

    private TestInputTopic<String, RawOrderBookDepthEvent> inputTopic(TopologyTestDriver driver) {
        return driver.createInputTopic(RAW_TOPIC, keySerde.serializer(), rawSerde.serializer());
    }

    private TestOutputTopic<String, AskQuote> askQuoteOutput(TopologyTestDriver driver) {
        return driver.createOutputTopic(ASK_QUOTE_TOPIC, keySerde.deserializer(), askQuoteSerde.deserializer());
    }

    private TestOutputTopic<String, AskOpportunity> askOpportunityOutput(TopologyTestDriver driver) {
        return driver.createOutputTopic(
                ASK_OPPORTUNITY_TOPIC,
                keySerde.deserializer(),
                askOpportunitySerde.deserializer());
    }

    private TestOutputTopic<String, ScoutWindowSummary> summaryOutput(TopologyTestDriver driver) {
        return driver.createOutputTopic(SUMMARY_TOPIC, keySerde.deserializer(), summarySerde.deserializer());
    }

    private RawOrderBookDepthEvent rawEvent(
            String symbol,
            List<OrderBookLevel> bids,
            List<OrderBookLevel> asks,
            Instant transactionTime) {
        return new RawOrderBookDepthEvent(
                UUID.randomUUID().toString(),
                symbol,
                transactionTime.minusMillis(5),
                transactionTime,
                10L,
                12L,
                9L,
                bids,
                asks,
                PROCESSED_AT);
    }
}
