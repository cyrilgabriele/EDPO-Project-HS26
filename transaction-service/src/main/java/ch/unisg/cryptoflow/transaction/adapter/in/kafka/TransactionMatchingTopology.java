package ch.unisg.cryptoflow.transaction.adapter.in.kafka;

import ch.unisg.cryptoflow.events.avro.MatchableAsk;
import ch.unisg.cryptoflow.events.avro.SpecificAvroSerde;
import ch.unisg.cryptoflow.transaction.avro.BuyBid;
import ch.unisg.cryptoflow.transaction.avro.OrderMatched;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class TransactionMatchingTopology {

    static final String PENDING_BIDS_STORE = "pending-buy-bids-by-symbol";
    static final String MATCHED_TRANSACTIONS_STORE = "matched-transactions";
    static final String ALLOCATED_ASKS_STORE = "allocated-asks";
    private static final String FIELD_SEPARATOR = "\\|";
    private static final String FIELD_JOINER = "|";

    private TransactionMatchingTopology() {
    }

    public static KStream<String, OrderMatched> build(
            StreamsBuilder builder,
            TransactionMatchingTopologyProperties properties) {
        Serde<String> keySerde = Serdes.String();
        SpecificAvroSerde<BuyBid> buyBidSerde = new SpecificAvroSerde<>(BuyBid.class, BuyBid.getClassSchema());
        SpecificAvroSerde<MatchableAsk> matchableAskSerde =
                new SpecificAvroSerde<>(MatchableAsk.class, MatchableAsk.getClassSchema());
        SpecificAvroSerde<OrderMatched> orderMatchedSerde =
                new SpecificAvroSerde<>(OrderMatched.class, OrderMatched.getClassSchema());

        addStateStores(builder);

        KStream<String, MatchCandidate> bids = builder
                .stream(properties.buyBidTopic(), Consumed.with(keySerde, buyBidSerde)
                        .withTimestampExtractor(new BuyBidTimestampExtractor()))
                .filter((key, bid) -> bid != null && bid.getSymbol() != null)
                .selectKey((key, bid) -> normalizeSymbol(bid.getSymbol()))
                .mapValues(MatchCandidate::bid);

        KStream<String, MatchCandidate> asks = builder
                .stream(properties.matchableAskTopic(), Consumed.with(keySerde, matchableAskSerde)
                        .withTimestampExtractor(new MatchableAskTimestampExtractor()))
                .filter((key, ask) -> ask != null && ask.getSymbol() != null)
                .selectKey((key, ask) -> normalizeSymbol(ask.getSymbol()))
                .mapValues(MatchCandidate::ask);

        KStream<String, OrderMatched> matches = bids.merge(asks)
                .transformValues(new MatchingTransformerSupplier(properties.validityWindow(), properties.gracePeriod()),
                        PENDING_BIDS_STORE, MATCHED_TRANSACTIONS_STORE, ALLOCATED_ASKS_STORE)
                .filter((symbol, match) -> match != null)
                .selectKey((symbol, match) -> match.getTransactionId());

        matches.to(properties.orderMatchedTopic(), Produced.with(keySerde, orderMatchedSerde));
        return matches;
    }

    private static void addStateStores(StreamsBuilder builder) {
        StoreBuilder<KeyValueStore<String, String>> pendingBids = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(PENDING_BIDS_STORE), Serdes.String(), Serdes.String());
        StoreBuilder<KeyValueStore<String, String>> matchedTransactions = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(MATCHED_TRANSACTIONS_STORE), Serdes.String(), Serdes.String());
        StoreBuilder<KeyValueStore<String, String>> allocatedAsks = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(ALLOCATED_ASKS_STORE), Serdes.String(), Serdes.String());
        builder.addStateStore(pendingBids);
        builder.addStateStore(matchedTransactions);
        builder.addStateStore(allocatedAsks);
    }

    private static String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    private static final class MatchingTransformerSupplier
            implements ValueTransformerWithKeySupplier<String, MatchCandidate, OrderMatched> {

        private final Duration validityWindow;
        private final Duration gracePeriod;

        private MatchingTransformerSupplier(Duration validityWindow, Duration gracePeriod) {
            this.validityWindow = validityWindow;
            this.gracePeriod = gracePeriod;
        }

        @Override
        public ValueTransformerWithKey<String, MatchCandidate, OrderMatched> get() {
            return new MatchingTransformer(validityWindow, gracePeriod);
        }
    }

    private static final class MatchingTransformer implements ValueTransformerWithKey<String, MatchCandidate, OrderMatched> {

        private final Duration validityWindow;
        private final Duration retentionWindow;
        private KeyValueStore<String, String> pendingBids;
        private KeyValueStore<String, String> matchedTransactions;
        private KeyValueStore<String, String> allocatedAsks;

        private MatchingTransformer(Duration validityWindow, Duration gracePeriod) {
            this.validityWindow = validityWindow;
            this.retentionWindow = validityWindow.plus(gracePeriod);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void init(ProcessorContext context) {
            this.pendingBids = (KeyValueStore<String, String>) context.getStateStore(PENDING_BIDS_STORE);
            this.matchedTransactions = (KeyValueStore<String, String>) context.getStateStore(MATCHED_TRANSACTIONS_STORE);
            this.allocatedAsks = (KeyValueStore<String, String>) context.getStateStore(ALLOCATED_ASKS_STORE);
        }

        @Override
        public OrderMatched transform(String symbol, MatchCandidate candidate) {
            if (candidate == null) {
                return null;
            }
            if (candidate.bid() != null) {
                rememberBid(symbol, candidate.bid());
                return null;
            }
            return allocateAsk(symbol, candidate.ask());
        }

        private void rememberBid(String symbol, BuyBid bid) {
            if (bid == null
                    || bid.getTransactionId() == null
                    || bid.getCreatedAt() == null
                    || matchedTransactions.get(bid.getTransactionId()) != null) {
                return;
            }
            List<PendingBid> bids = readPending(symbol);
            bids.removeIf(existing -> existing.transactionId().equals(bid.getTransactionId()));
            bids.add(PendingBid.from(bid));
            writePending(symbol, bids);
        }

        private OrderMatched allocateAsk(String symbol, MatchableAsk ask) {
            if (ask == null
                    || ask.getAskQuoteId() == null
                    || ask.getEventTime() == null
                    || allocatedAsks.get(ask.getAskQuoteId()) != null) {
                return null;
            }

            List<PendingBid> bids = readPending(symbol);
            Instant retentionCutoff = ask.getEventTime().minus(retentionWindow);
            bids.removeIf(bid -> matchedTransactions.get(bid.transactionId()) != null
                    || bid.createdAt().isBefore(retentionCutoff));

            PendingBid winner = bids.stream()
                    .filter(bid -> eligible(bid, ask))
                    .max(Comparator
                            .comparing(PendingBid::bidPrice)
                            .thenComparing(PendingBid::createdAt, Comparator.reverseOrder())
                            .thenComparing(PendingBid::transactionId, Comparator.reverseOrder()))
                    .orElse(null);

            if (winner == null) {
                writePending(symbol, bids);
                return null;
            }

            bids.removeIf(bid -> bid.transactionId().equals(winner.transactionId()));
            writePending(symbol, bids);
            matchedTransactions.put(winner.transactionId(), ask.getAskQuoteId());
            allocatedAsks.put(ask.getAskQuoteId(), winner.transactionId());

            return OrderMatched.newBuilder()
                    .setTransactionId(winner.transactionId())
                    .setAskQuoteId(ask.getAskQuoteId())
                    .setSymbol(symbol)
                    .setMatchedPrice(ask.getAskPrice())
                    .setMatchedQuantity(winner.bidQuantity())
                    .setBidCreatedAt(winner.createdAt())
                    .setAskEventTime(ask.getEventTime())
                    .setSourceVenue(ask.getSourceVenue())
                    .build();
        }

        private boolean eligible(PendingBid bid, MatchableAsk ask) {
            Instant askTime = ask.getEventTime();
            return !askTime.isBefore(bid.createdAt())
                    && !askTime.isAfter(bid.createdAt().plus(validityWindow))
                    && bid.bidPrice().compareTo(ask.getAskPrice()) >= 0
                    && bid.bidQuantity().compareTo(ask.getAskQuantity()) <= 0;
        }

        private List<PendingBid> readPending(String symbol) {
            String serialized = pendingBids.get(symbol);
            if (serialized == null || serialized.isBlank()) {
                return new ArrayList<>();
            }
            List<PendingBid> bids = new ArrayList<>();
            for (String line : serialized.split("\n")) {
                if (!line.isBlank()) {
                    bids.add(PendingBid.deserialize(line));
                }
            }
            return bids;
        }

        private void writePending(String symbol, List<PendingBid> bids) {
            if (bids.isEmpty()) {
                pendingBids.delete(symbol);
                return;
            }
            StringBuilder builder = new StringBuilder();
            for (PendingBid bid : bids) {
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append(bid.serialize());
            }
            pendingBids.put(symbol, builder.toString());
        }

        @Override
        public void close() {
        }
    }

    private record MatchCandidate(BuyBid bid, MatchableAsk ask) {
        static MatchCandidate bid(BuyBid bid) {
            return new MatchCandidate(bid, null);
        }

        static MatchCandidate ask(MatchableAsk ask) {
            return new MatchCandidate(null, ask);
        }
    }

    private record PendingBid(
            String transactionId,
            String userId,
            BigDecimal bidPrice,
            BigDecimal bidQuantity,
            Instant createdAt) {

        static PendingBid from(BuyBid bid) {
            return new PendingBid(
                    bid.getTransactionId(),
                    bid.getUserId(),
                    bid.getBidPrice(),
                    bid.getBidQuantity(),
                    bid.getCreatedAt());
        }

        static PendingBid deserialize(String serialized) {
            String[] parts = serialized.split(FIELD_SEPARATOR, -1);
            return new PendingBid(
                    parts[0],
                    parts[1],
                    new BigDecimal(parts[2]),
                    new BigDecimal(parts[3]),
                    Instant.ofEpochMilli(Long.parseLong(parts[4])));
        }

        String serialize() {
            return transactionId
                    + FIELD_JOINER + userId
                    + FIELD_JOINER + bidPrice.toPlainString()
                    + FIELD_JOINER + bidQuantity.toPlainString()
                    + FIELD_JOINER + createdAt.toEpochMilli();
        }
    }
}
