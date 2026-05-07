package ch.unisg.cryptoflow.marketscout.adapter.in.kafka;

import ch.unisg.cryptoflow.events.OrderBookLevel;
import ch.unisg.cryptoflow.events.RawOrderBookDepthEvent;
import ch.unisg.cryptoflow.events.avro.MatchableAsk;
import ch.unisg.cryptoflow.events.avro.SpecificAvroSerde;
import ch.unisg.cryptoflow.marketscout.avro.AskOpportunity;
import ch.unisg.cryptoflow.marketscout.avro.AskQuote;
import ch.unisg.cryptoflow.marketscout.avro.ScoutWindowSummary;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MarketScoutTopology {

    private static final int DECIMAL_SCALE = 18;

    private MarketScoutTopology() {
    }

    public static KStream<String, RawOrderBookDepthEvent> build(
            StreamsBuilder builder,
            MarketScoutTopologyProperties properties,
            Clock clock) {
        Serde<String> keySerde = Serdes.String();
        JsonSerde<RawOrderBookDepthEvent> rawSerde = new JsonSerde<>(RawOrderBookDepthEvent.class);
        rawSerde.noTypeInfo();

        SpecificAvroSerde<AskQuote> askQuoteSerde = new SpecificAvroSerde<>(AskQuote.class, AskQuote.getClassSchema());
        SpecificAvroSerde<AskOpportunity> askOpportunitySerde =
                new SpecificAvroSerde<>(AskOpportunity.class, AskOpportunity.getClassSchema());
        SpecificAvroSerde<ScoutWindowSummary> scoutSummarySerde =
                new SpecificAvroSerde<>(ScoutWindowSummary.class, ScoutWindowSummary.getClassSchema());
        SpecificAvroSerde<MatchableAsk> matchableAskSerde =
                new SpecificAvroSerde<>(MatchableAsk.class, MatchableAsk.getClassSchema());

        KStream<String, RawOrderBookDepthEvent> rawEvents = builder.stream(
                properties.rawTopic(),
                Consumed.with(keySerde, rawSerde)
                        .withTimestampExtractor(new RawOrderBookDepthTimestampExtractor()));

        KStream<String, AskQuote> askQuotes = rawEvents
                .filter((key, event) -> event != null
                        && event.symbol() != null
                        && event.asks() != null
                        && !event.asks().isEmpty())
                .flatMapValues(event -> toAskQuotes(event, properties.sourceVenue(), clock.instant()));

        askQuotes.to(properties.askQuoteTopic(), Produced.with(keySerde, askQuoteSerde));

        askQuotes
                .mapValues(MarketScoutTopology::toMatchableAsk)
                .to(properties.matchableAskTopic(), Produced.with(keySerde, matchableAskSerde));

        KStream<String, AskOpportunity> askOpportunities = askQuotes
                .filter((key, quote) -> quote.getPrice().compareTo(properties.askThreshold()) <= 0)
                .mapValues(quote -> toAskOpportunity(quote, properties.askThreshold()));

        askOpportunities.to(properties.askOpportunityTopic(), Produced.with(keySerde, askOpportunitySerde));

        askOpportunities
                .groupByKey(Grouped.with(keySerde, askOpportunitySerde))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(properties.summaryWindow()))
                .aggregate(
                        MarketScoutTopology::emptySummary,
                        (symbol, opportunity, summary) -> updateSummary(symbol, opportunity, summary),
                        Materialized.with(keySerde, scoutSummarySerde))
                .toStream()
                .mapValues(MarketScoutTopology::withWindowBounds)
                .selectKey((windowedSymbol, summary) -> windowedSymbol.key())
                .to(properties.scoutSummaryTopic(), Produced.with(keySerde, scoutSummarySerde));

        return rawEvents;
    }

    private static List<AskQuote> toAskQuotes(RawOrderBookDepthEvent event, String sourceVenue, Instant processedAt) {
        AskContext askContext = askContext(event.asks());
        if (askContext == null) {
            return List.of();
        }

        List<AskQuote> quotes = new ArrayList<>();
        for (int askLevelIndex = 0; askLevelIndex < event.asks().size(); askLevelIndex++) {
            OrderBookLevel ask = event.asks().get(askLevelIndex);
            if (ask == null || ask.price() == null || ask.quantity() == null) {
                continue;
            }

            BigDecimal price = normalize(ask.price());
            BigDecimal quantity = normalize(ask.quantity());
            quotes.add(AskQuote.newBuilder()
                    .setAskQuoteId(askQuoteId(event, askLevelIndex))
                    .setSymbol(event.symbol())
                    .setPrice(price)
                    .setQuantity(quantity)
                    .setBestAskPrice(askContext.bestAskPrice())
                    .setBestAskQuantity(askContext.bestAskQuantity())
                    .setAskNotional(normalizeProduct(price.multiply(quantity)))
                    .setTransactionTime(nonNullInstant(event.transactionTime()))
                    .setEventTime(nonNullInstant(event.eventTime()))
                    .setSourceVenue(sourceVenue)
                    .setProcessedAt(processedAt)
                    .build());
        }
        return quotes;
    }

    private static AskOpportunity toAskOpportunity(AskQuote quote, BigDecimal threshold) {
        return AskOpportunity.newBuilder()
                .setAskQuoteId(quote.getAskQuoteId())
                .setSymbol(quote.getSymbol())
                .setPrice(quote.getPrice())
                .setQuantity(quote.getQuantity())
                .setThreshold(normalize(threshold))
                .setBestAskPrice(quote.getBestAskPrice())
                .setBestAskQuantity(quote.getBestAskQuantity())
                .setAskNotional(quote.getAskNotional())
                .setTransactionTime(quote.getTransactionTime())
                .setEventTime(quote.getEventTime())
                .setSourceVenue(quote.getSourceVenue())
                .setProcessedAt(quote.getProcessedAt())
                .build();
    }

    private static MatchableAsk toMatchableAsk(AskQuote quote) {
        return MatchableAsk.newBuilder()
                .setAskQuoteId(quote.getAskQuoteId())
                .setSymbol(quote.getSymbol())
                .setAskPrice(quote.getPrice())
                .setAskQuantity(quote.getQuantity())
                .setEventTime(quote.getEventTime())
                .setSourceVenue(quote.getSourceVenue())
                .build();
    }

    private static ScoutWindowSummary emptySummary() {
        return ScoutWindowSummary.newBuilder()
                .setSymbol("")
                .setWindowStart(Instant.EPOCH)
                .setWindowEnd(Instant.EPOCH)
                .setOpportunityCount(0L)
                .setMinAskPrice(null)
                .build();
    }

    private static ScoutWindowSummary updateSummary(
            String symbol,
            AskOpportunity opportunity,
            ScoutWindowSummary summary) {
        BigDecimal currentMin = summary.getMinAskPrice();
        BigDecimal opportunityPrice = opportunity.getPrice();
        BigDecimal minPrice = currentMin == null || opportunityPrice.compareTo(currentMin) < 0
                ? opportunityPrice
                : currentMin;

        return ScoutWindowSummary.newBuilder()
                .setSymbol(symbol)
                .setWindowStart(summary.getWindowStart())
                .setWindowEnd(summary.getWindowEnd())
                .setOpportunityCount(summary.getOpportunityCount() + 1)
                .setMinAskPrice(minPrice)
                .build();
    }

    private static ScoutWindowSummary withWindowBounds(Windowed<String> windowedSymbol, ScoutWindowSummary summary) {
        return ScoutWindowSummary.newBuilder(summary)
                .setSymbol(windowedSymbol.key())
                .setWindowStart(Instant.ofEpochMilli(windowedSymbol.window().start()))
                .setWindowEnd(Instant.ofEpochMilli(windowedSymbol.window().end()))
                .build();
    }

    private static AskContext askContext(List<OrderBookLevel> asks) {
        for (OrderBookLevel ask : asks) {
            if (ask != null && ask.price() != null && ask.quantity() != null) {
                return new AskContext(normalize(ask.price()), normalize(ask.quantity()));
            }
        }
        return null;
    }

    private static BigDecimal normalize(BigDecimal decimal) {
        return decimal.setScale(DECIMAL_SCALE, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal normalizeProduct(BigDecimal decimal) {
        return decimal.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    private static Instant nonNullInstant(Instant instant) {
        return instant == null ? Instant.EPOCH : instant;
    }

    private static String askQuoteId(RawOrderBookDepthEvent event, int askLevelIndex) {
        String eventIdentity = event.eventId() == null || event.eventId().isBlank()
                ? event.symbol() + "-" + event.eventTime() + "-" + event.firstUpdateId() + "-" + event.finalUpdateId()
                : event.eventId();
        return eventIdentity + "-ask-" + askLevelIndex;
    }

    private record AskContext(BigDecimal bestAskPrice, BigDecimal bestAskQuantity) {
    }
}
