package ch.unisg.cryptoflow.transaction.adapter.in.kafka;

import ch.unisg.cryptoflow.events.avro.MatchableAsk;
import ch.unisg.cryptoflow.events.avro.SpecificAvroSerde;
import ch.unisg.cryptoflow.transaction.avro.BuyBid;
import ch.unisg.cryptoflow.transaction.avro.OrderMatched;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionMatchingTopologyTests {

    private static final String BUY_BID_TOPIC = "transaction.buy-bids";
    private static final String MATCHABLE_ASK_TOPIC = "crypto.scout.matchable-asks";
    private static final String ORDER_MATCHED_TOPIC = "transaction.order-matched";
    private static final Instant T0 = Instant.parse("2026-05-03T10:15:00Z");

    private final Serde<String> keySerde = Serdes.String();
    private final SpecificAvroSerde<BuyBid> buyBidSerde =
            new SpecificAvroSerde<>(BuyBid.class, BuyBid.getClassSchema());
    private final SpecificAvroSerde<MatchableAsk> matchableAskSerde =
            new SpecificAvroSerde<>(MatchableAsk.class, MatchableAsk.getClassSchema());
    private final SpecificAvroSerde<OrderMatched> orderMatchedSerde =
            new SpecificAvroSerde<>(OrderMatched.class, OrderMatched.getClassSchema());

    @Test
    void symbolKeyedJoinSucceedsForMatchingBidAndAsk() {
        try (TopologyTestDriver driver = testDriver()) {
            bids(driver).pipeInput("ETHUSDT", bid("tx-1", "ETHUSDT", "101.00", "1.00", T0));
            asks(driver).pipeInput("ETHUSDT", ask("ask-1", "ETHUSDT", "100.00", "2.00", T0.plusSeconds(5)));

            OrderMatched match = matches(driver).readValue();

            assertThat(match.getTransactionId()).isEqualTo("tx-1");
            assertThat(match.getAskQuoteId()).isEqualTo("ask-1");
            assertThat(match.getMatchedPrice()).isEqualByComparingTo("100.00");
            assertThat(matches(driver).isEmpty()).isTrue();
        }
    }

    @Test
    void noMatchForDifferentSymbols() {
        try (TopologyTestDriver driver = testDriver()) {
            bids(driver).pipeInput("ETHUSDT", bid("tx-1", "ETHUSDT", "101.00", "1.00", T0));
            asks(driver).pipeInput("BTCUSDT", ask("ask-1", "BTCUSDT", "100.00", "2.00", T0.plusSeconds(5)));

            assertThat(matches(driver).isEmpty()).isTrue();
        }
    }

    @Test
    void noMatchForAskBeforeBidCreation() {
        try (TopologyTestDriver driver = testDriver()) {
            bids(driver).pipeInput("ETHUSDT", bid("tx-1", "ETHUSDT", "101.00", "1.00", T0));
            asks(driver).pipeInput("ETHUSDT", ask("ask-1", "ETHUSDT", "100.00", "2.00", T0.minusMillis(1)));

            assertThat(matches(driver).isEmpty()).isTrue();
        }
    }

    @Test
    void noMatchAfterThirtySecondValidityWindow() {
        try (TopologyTestDriver driver = testDriver()) {
            bids(driver).pipeInput("ETHUSDT", bid("tx-1", "ETHUSDT", "101.00", "1.00", T0));
            asks(driver).pipeInput("ETHUSDT", ask("ask-1", "ETHUSDT", "100.00", "2.00", T0.plusSeconds(31)));

            assertThat(matches(driver).isEmpty()).isTrue();
        }
    }

    @Test
    void graceHandlesLateAskArrivalWithinFallbackMargin() {
        try (TopologyTestDriver driver = testDriver()) {
            bids(driver).pipeInput("ETHUSDT", bid("tx-1", "ETHUSDT", "101.00", "1.00", T0));
            asks(driver).pipeInput("ETHUSDT", ask("ask-future", "ETHUSDT", "200.00", "2.00", T0.plusSeconds(34)));
            asks(driver).pipeInput("ETHUSDT", ask("ask-late", "ETHUSDT", "100.00", "2.00", T0.plusSeconds(30)));

            OrderMatched match = matches(driver).readValue();

            assertThat(match.getTransactionId()).isEqualTo("tx-1");
            assertThat(match.getAskQuoteId()).isEqualTo("ask-late");
            assertThat(matches(driver).isEmpty()).isTrue();
        }
    }

    @Test
    void priceTimePriorityChoosesHighestBidThenEarliestOrder() {
        try (TopologyTestDriver driver = testDriver()) {
            bids(driver).pipeInput("ETHUSDT", bid("tx-early-low", "ETHUSDT", "101.00", "1.00", T0));
            bids(driver).pipeInput("ETHUSDT", bid("tx-late-high", "ETHUSDT", "102.00", "1.00", T0.plusSeconds(1)));
            asks(driver).pipeInput("ETHUSDT", ask("ask-1", "ETHUSDT", "100.00", "2.00", T0.plusSeconds(2)));

            assertThat(matches(driver).readValue().getTransactionId()).isEqualTo("tx-late-high");

            bids(driver).pipeInput("ETHUSDT", bid("tx-a", "ETHUSDT", "102.00", "1.00", T0.plusSeconds(3)));
            bids(driver).pipeInput("ETHUSDT", bid("tx-b", "ETHUSDT", "102.00", "1.00", T0.plusSeconds(4)));
            asks(driver).pipeInput("ETHUSDT", ask("ask-2", "ETHUSDT", "100.00", "2.00", T0.plusSeconds(5)));

            assertThat(matches(driver).readValue().getTransactionId()).isEqualTo("tx-a");
        }
    }

    @Test
    void losingBidsRemainPendingAndCanMatchLaterAsks() {
        try (TopologyTestDriver driver = testDriver()) {
            bids(driver).pipeInput("ETHUSDT", bid("tx-loser", "ETHUSDT", "101.00", "1.00", T0));
            bids(driver).pipeInput("ETHUSDT", bid("tx-winner", "ETHUSDT", "102.00", "1.00", T0.plusSeconds(1)));
            asks(driver).pipeInput("ETHUSDT", ask("ask-1", "ETHUSDT", "100.00", "2.00", T0.plusSeconds(2)));
            asks(driver).pipeInput("ETHUSDT", ask("ask-2", "ETHUSDT", "100.00", "2.00", T0.plusSeconds(3)));

            assertThat(matches(driver).readValue().getTransactionId()).isEqualTo("tx-winner");
            assertThat(matches(driver).readValue().getTransactionId()).isEqualTo("tx-loser");
        }
    }

    @Test
    void duplicateMatchesForAlreadyMatchedTransactionAreIgnored() {
        try (TopologyTestDriver driver = testDriver()) {
            bids(driver).pipeInput("ETHUSDT", bid("tx-1", "ETHUSDT", "101.00", "1.00", T0));
            asks(driver).pipeInput("ETHUSDT", ask("ask-1", "ETHUSDT", "100.00", "2.00", T0.plusSeconds(1)));
            asks(driver).pipeInput("ETHUSDT", ask("ask-2", "ETHUSDT", "100.00", "2.00", T0.plusSeconds(2)));

            assertThat(matches(driver).readValue().getTransactionId()).isEqualTo("tx-1");
            assertThat(matches(driver).isEmpty()).isTrue();
        }
    }

    private TopologyTestDriver testDriver() {
        StreamsBuilder builder = new StreamsBuilder();
        TransactionMatchingTopology.build(builder, new TransactionMatchingTopologyProperties(
                BUY_BID_TOPIC,
                MATCHABLE_ASK_TOPIC,
                ORDER_MATCHED_TOPIC,
                Duration.ofSeconds(30),
                Duration.ofSeconds(5)));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "transaction-matching-topology-test-" + UUID.randomUUID());
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        properties.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);
        return new TopologyTestDriver(builder.build(), properties);
    }

    private TestInputTopic<String, BuyBid> bids(TopologyTestDriver driver) {
        return driver.createInputTopic(BUY_BID_TOPIC, keySerde.serializer(), buyBidSerde.serializer());
    }

    private TestInputTopic<String, MatchableAsk> asks(TopologyTestDriver driver) {
        return driver.createInputTopic(MATCHABLE_ASK_TOPIC, keySerde.serializer(), matchableAskSerde.serializer());
    }

    private TestOutputTopic<String, OrderMatched> matches(TopologyTestDriver driver) {
        return driver.createOutputTopic(ORDER_MATCHED_TOPIC, keySerde.deserializer(), orderMatchedSerde.deserializer());
    }

    private BuyBid bid(String transactionId, String symbol, String price, String quantity, Instant createdAt) {
        return BuyBid.newBuilder()
                .setTransactionId(transactionId)
                .setUserId("user-1")
                .setSymbol(symbol)
                .setBidPrice(decimal(price))
                .setBidQuantity(decimal(quantity))
                .setCreatedAt(createdAt)
                .build();
    }

    private MatchableAsk ask(String askQuoteId, String symbol, String price, String quantity, Instant eventTime) {
        return MatchableAsk.newBuilder()
                .setAskQuoteId(askQuoteId)
                .setSymbol(symbol)
                .setAskPrice(decimal(price))
                .setAskQuantity(decimal(quantity))
                .setEventTime(eventTime)
                .setSourceVenue("BINANCE_USD_M_FUTURES")
                .build();
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(18);
    }
}
