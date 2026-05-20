package ch.unisg.cryptoflow.portfolio.streams;

import ch.unisg.cryptoflow.events.avro.PortfolioValue;
import ch.unisg.cryptoflow.events.avro.PositionValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure adder/subtractor used by the final {@code KTable.groupBy(...).aggregate(...)}
 * step of the scope-04 valuation topology. Maintains a {@link PortfolioValue}
 * with a per-symbol breakdown and a running USDT total.
 *
 * <p>The Kafka Streams runtime calls {@code subtractor} with the prior
 * upstream value and {@code adder} with the new one, so a symbol update
 * round-trips cleanly: subtractor removes the old contribution, adder inserts
 * the new one.
 */
public final class PortfolioValueAggregator {

    private PortfolioValueAggregator() {}

    public static PortfolioValue empty(String userId) {
        return PortfolioValue.newBuilder()
                .setUserId(userId)
                .setTotalUsdt(BigDecimal.ZERO)
                .setBreakdown(new ArrayList<>())
                .setAsOf(Instant.EPOCH)
                .build();
    }

    public static PortfolioValue add(String userId, PositionValue pv, PortfolioValue current, Instant asOf) {
        List<PositionValue> next = new ArrayList<>(current.getBreakdown());
        next.removeIf(existing -> existing.getSymbol().equals(pv.getSymbol()));
        next.add(pv);
        next.sort(Comparator.comparing(PositionValue::getSymbol));
        return PortfolioValue.newBuilder()
                .setUserId(userId)
                .setBreakdown(next)
                .setTotalUsdt(sumValues(next))
                .setAsOf(asOf)
                .build();
    }

    public static PortfolioValue subtract(String userId, PositionValue pv, PortfolioValue current, Instant asOf) {
        List<PositionValue> next = new ArrayList<>(current.getBreakdown());
        next.removeIf(existing -> existing.getSymbol().equals(pv.getSymbol()));
        return PortfolioValue.newBuilder()
                .setUserId(userId)
                .setBreakdown(next)
                .setTotalUsdt(sumValues(next))
                .setAsOf(asOf)
                .build();
    }

    private static BigDecimal sumValues(List<PositionValue> positions) {
        BigDecimal total = BigDecimal.ZERO;
        for (PositionValue p : positions) {
            total = total.add(p.getValueUsdt());
        }
        return total;
    }
}
