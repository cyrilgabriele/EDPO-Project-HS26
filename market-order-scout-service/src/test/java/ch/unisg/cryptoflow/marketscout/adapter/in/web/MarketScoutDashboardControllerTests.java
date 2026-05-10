package ch.unisg.cryptoflow.marketscout.adapter.in.web;

import ch.unisg.cryptoflow.marketscout.domain.ScoutDashboardSample;
import ch.unisg.cryptoflow.marketscout.domain.ScoutDashboardStats;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class MarketScoutDashboardControllerTests {

    @Test
    void dashboardReturnsWellShapedEmptyState() {
        MarketScoutDashboardController controller = controller(List.of());

        Map<String, Object> dashboard = controller.dashboard();

        assertThat(dashboard.get("service")).isEqualTo("market-order-scout-service");
        assertThat(dashboard.get("refreshIntervalMillis")).isEqualTo(3000);
        Map<String, Object> topics = (Map<String, Object>) dashboard.get("topics");
        assertThat(topics).containsEntry("windowSummary", "summary-topic");
        assertThat((List<?>) dashboard.get("symbols")).isEmpty();
    }

    @Test
    void dashboardIncludesSymbolMomentsAndHistogramBins() {
        ScoutDashboardStats stats = ScoutDashboardStats.fromSamples("BTCUSDT", List.of(
                sample(0, 1.0),
                sample(30, 2.0),
                sample(60, 3.0)));
        MarketScoutDashboardController controller = controller(List.of(stats));

        Map<String, Object> dashboard = controller.dashboard();
        List<?> symbols = (List<?>) dashboard.get("symbols");
        Map<?, ?> symbol = (Map<?, ?>) symbols.getFirst();

        assertThat(symbol.get("symbol")).isEqualTo("BTCUSDT");
        assertThat(symbol.get("count")).isEqualTo(3L);
        Map<String, Object> moments = (Map<String, Object>) symbol.get("moments");
        assertThat(moments).containsKeys("mean", "variance", "skewness", "kurtosis");
        assertThat((List<?>) symbol.get("histogramBins")).isNotEmpty();
    }

    private static MarketScoutDashboardController controller(List<ScoutDashboardStats> stats) {
        return new MarketScoutDashboardController(
                () -> stats,
                "raw-topic",
                "quotes-topic",
                "matchable-topic",
                "opportunity-topic",
                "summary-topic");
    }

    private static ScoutDashboardSample sample(long startOffsetSeconds, double value) {
        Instant start = Instant.parse("2026-05-03T10:00:00Z").plusSeconds(startOffsetSeconds);
        return new ScoutDashboardSample(start, start.plusSeconds(30), value);
    }
}
