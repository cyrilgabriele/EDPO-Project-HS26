package ch.unisg.cryptoflow.portfolio.streams;

import ch.unisg.cryptoflow.events.avro.PortfolioValue;
import ch.unisg.cryptoflow.events.avro.PositionValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioValueAggregatorTest {

    private static final Instant T1 = Instant.parse("2026-05-20T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-05-20T10:00:01Z");

    @Test
    void emptyInitializerHasZeroTotalAndNoBreakdown() {
        PortfolioValue empty = PortfolioValueAggregator.empty("u-1");
        assertThat(empty.getUserId()).isEqualTo("u-1");
        assertThat(empty.getTotalUsdt()).isEqualByComparingTo("0");
        assertThat(empty.getBreakdown()).isEmpty();
    }

    @Test
    void addInsertsNewPositionAndRecomputesTotal() {
        PortfolioValue start = PortfolioValueAggregator.empty("u-1");
        PortfolioValue after = PortfolioValueAggregator.add(
                "u-1", position("BTCUSDT", "0.5", "30000"), start, T1);

        assertThat(after.getTotalUsdt()).isEqualByComparingTo("30000");
        assertThat(after.getBreakdown()).hasSize(1);
        assertThat(after.getBreakdown().get(0).getSymbol()).isEqualTo("BTCUSDT");
        assertThat(after.getBreakdown().get(0).getValueUsdt()).isEqualByComparingTo("30000");
        assertThat(after.getAsOf()).isEqualTo(T1);
    }

    @Test
    void addReplacesExistingPositionForSameSymbol() {
        PortfolioValue start = PortfolioValueAggregator.add(
                "u-1", position("BTCUSDT", "0.5", "30000"),
                PortfolioValueAggregator.empty("u-1"), T1);

        PortfolioValue after = PortfolioValueAggregator.add(
                "u-1", position("BTCUSDT", "0.5", "31000"), start, T2);

        assertThat(after.getBreakdown()).hasSize(1);
        assertThat(after.getBreakdown().get(0).getValueUsdt()).isEqualByComparingTo("31000");
        assertThat(after.getTotalUsdt()).isEqualByComparingTo("31000");
    }

    @Test
    void subtractRemovesPositionAndRecomputesTotal() {
        PortfolioValue twoPositions = PortfolioValueAggregator.add(
                "u-1", position("ETHUSDT", "10", "2000"),
                PortfolioValueAggregator.add(
                        "u-1", position("BTCUSDT", "0.5", "30000"),
                        PortfolioValueAggregator.empty("u-1"), T1),
                T1);
        assertThat(twoPositions.getTotalUsdt()).isEqualByComparingTo("32000");

        PortfolioValue after = PortfolioValueAggregator.subtract(
                "u-1", position("BTCUSDT", "0.5", "30000"), twoPositions, T2);

        assertThat(after.getBreakdown()).hasSize(1);
        assertThat(after.getBreakdown().get(0).getSymbol()).isEqualTo("ETHUSDT");
        assertThat(after.getTotalUsdt()).isEqualByComparingTo("2000");
    }

    @Test
    void breakdownIsSortedBySymbol() {
        PortfolioValue v = PortfolioValueAggregator.add(
                "u-1", position("ETHUSDT", "1", "2000"),
                PortfolioValueAggregator.add(
                        "u-1", position("BTCUSDT", "0.1", "6000"),
                        PortfolioValueAggregator.empty("u-1"), T1),
                T2);

        assertThat(v.getBreakdown()).extracting(PositionValue::getSymbol)
                .containsExactly("BTCUSDT", "ETHUSDT");
    }

    private static PositionValue position(String symbol, String qty, String value) {
        return PositionValue.newBuilder()
                .setSymbol(symbol)
                .setQuantity(new BigDecimal(qty))
                .setValueUsdt(new BigDecimal(value))
                .build();
    }
}
