package ch.unisg.cryptoflow.marketscout.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ScoutDashboardStatsTests {

    @Test
    void computesMomentsFromRollingSamples() {
        ScoutDashboardStats stats = ScoutDashboardStats.fromSamples("BTCUSDT", List.of(
                sample(0, 1.0),
                sample(30, 2.0),
                sample(60, 3.0)));

        assertThat(stats.count()).isEqualTo(3);
        assertThat(stats.mean()).isCloseTo(2.0, within(0.000000001));
        assertThat(stats.variance()).isCloseTo(2.0 / 3.0, within(0.000000001));
        assertThat(stats.skewness()).isCloseTo(0.0, within(0.000000001));
        assertThat(stats.kurtosis()).isCloseTo(1.5, within(0.000000001));
    }

    @Test
    void flatSampleFallsBackToOneHistogramBin() {
        ScoutDashboardStats stats = ScoutDashboardStats.fromSamples("BTCUSDT", List.of(
                sample(0, 2.0),
                sample(30, 2.0),
                sample(60, 2.0)));

        assertThat(stats.histogramBins()).hasSize(1);
        assertThat(stats.histogramBins().getFirst().count()).isEqualTo(3);
    }

    @Test
    void zeroIqrSampleFallsBackToScottRule() {
        ScoutDashboardStats stats = ScoutDashboardStats.fromSamples("BTCUSDT", List.of(
                sample(0, 1.0),
                sample(30, 1.0),
                sample(60, 1.0),
                sample(90, 1.0),
                sample(120, 2.0)));

        assertThat(stats.histogramBins()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(stats.histogramBins()).hasSizeLessThanOrEqualTo(50);
        assertThat(stats.histogramBins().stream().mapToLong(ScoutDashboardHistogramBin::count).sum()).isEqualTo(5);
    }

    @Test
    void binCountIsCappedForWideReadableHistograms() {
        List<ScoutDashboardSample> samples = java.util.stream.IntStream.range(0, 200)
                .mapToObj(i -> sample(i * 30L, i))
                .toList();

        ScoutDashboardStats stats = ScoutDashboardStats.fromSamples("BTCUSDT", samples);

        assertThat(stats.histogramBins()).hasSizeLessThanOrEqualTo(50);
    }

    private static ScoutDashboardSample sample(long startOffsetSeconds, double value) {
        Instant start = Instant.parse("2026-05-03T10:00:00Z").plusSeconds(startOffsetSeconds);
        return new ScoutDashboardSample(start, start.plusSeconds(30), value);
    }
}
