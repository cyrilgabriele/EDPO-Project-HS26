package ch.unisg.cryptoflow.marketscout.domain;

import ch.unisg.cryptoflow.marketscout.avro.ScoutWindowSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ScoutDashboardStats(
        String symbol,
        List<ScoutDashboardSample> samples,
        long count,
        double mean,
        double variance,
        double skewness,
        double kurtosis,
        List<ScoutDashboardHistogramBin> histogramBins
) {

    public static final int MAX_SAMPLES = 50;
    private static final int MIN_BINS = 5;
    private static final int MAX_BINS = 50;

    public static ScoutDashboardStats empty() {
        return fromSamples("", List.of());
    }

    public ScoutDashboardStats withSummary(String symbol, ScoutWindowSummary summary) {
        BigDecimal minAskPrice = summary.getMinAskPrice();
        if (minAskPrice == null || minAskPrice.signum() <= 0) {
            return this;
        }

        ScoutDashboardSample replacement = new ScoutDashboardSample(
                nonNullInstant(summary.getWindowStart()),
                nonNullInstant(summary.getWindowEnd()),
                Math.log(minAskPrice.doubleValue()));

        Map<String, ScoutDashboardSample> byWindow = new LinkedHashMap<>();
        for (ScoutDashboardSample sample : samples) {
            byWindow.put(sample.windowId(), sample);
        }
        byWindow.put(replacement.windowId(), replacement);

        List<ScoutDashboardSample> retained = new ArrayList<>(byWindow.values());
        retained.sort(Comparator
                .comparing(ScoutDashboardSample::windowEnd)
                .thenComparing(ScoutDashboardSample::windowStart));
        while (retained.size() > MAX_SAMPLES) {
            retained.removeFirst();
        }
        return fromSamples(symbol, retained);
    }

    public static ScoutDashboardStats fromSamples(String symbol, List<ScoutDashboardSample> samples) {
        List<ScoutDashboardSample> sorted = samples.stream()
                .sorted(Comparator
                        .comparing(ScoutDashboardSample::windowEnd)
                        .thenComparing(ScoutDashboardSample::windowStart))
                .toList();
        List<Double> values = sorted.stream()
                .map(ScoutDashboardSample::logMinAskPrice)
                .toList();

        long count = values.size();
        if (count == 0) {
            return new ScoutDashboardStats(symbol, sorted, 0, 0.0, 0.0, 0.0, 0.0, List.of());
        }

        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double secondMoment = 0.0;
        double thirdMoment = 0.0;
        double fourthMoment = 0.0;
        for (double value : values) {
            double delta = value - mean;
            double delta2 = delta * delta;
            secondMoment += delta2;
            thirdMoment += delta2 * delta;
            fourthMoment += delta2 * delta2;
        }

        double variance = secondMoment / count;
        double skewness = variance == 0.0 ? 0.0 : (thirdMoment / count) / Math.pow(variance, 1.5);
        double kurtosis = variance == 0.0 ? 0.0 : (fourthMoment / count) / (variance * variance);

        return new ScoutDashboardStats(
                symbol,
                sorted,
                count,
                mean,
                variance,
                skewness,
                kurtosis,
                histogram(values));
    }

    private static List<ScoutDashboardHistogramBin> histogram(List<Double> values) {
        int n = values.size();
        if (n == 0) {
            return List.of();
        }

        List<Double> sorted = values.stream().sorted().toList();
        double min = sorted.getFirst();
        double max = sorted.getLast();
        if (Double.compare(min, max) == 0) {
            return List.of(new ScoutDashboardHistogramBin(min, max, n));
        }

        double iqr = percentile(sorted, 0.75) - percentile(sorted, 0.25);
        double binWidth = iqr == 0.0 ? scottBinWidth(sorted) : 2.0 * iqr / Math.cbrt(n);
        if (binWidth <= 0.0 || !Double.isFinite(binWidth)) {
            return List.of(new ScoutDashboardHistogramBin(min, max, n));
        }

        int binCount = (int) Math.ceil((max - min) / binWidth);
        binCount = Math.max(MIN_BINS, Math.min(MAX_BINS, binCount));
        double actualWidth = (max - min) / binCount;

        long[] counts = new long[binCount];
        for (double value : sorted) {
            int index = value == max ? binCount - 1 : (int) ((value - min) / actualWidth);
            counts[Math.max(0, Math.min(binCount - 1, index))]++;
        }

        List<ScoutDashboardHistogramBin> bins = new ArrayList<>();
        for (int i = 0; i < binCount; i++) {
            double lower = min + (actualWidth * i);
            double upper = i == binCount - 1 ? max : lower + actualWidth;
            bins.add(new ScoutDashboardHistogramBin(lower, upper, counts[i]));
        }
        return List.copyOf(bins);
    }

    private static double scottBinWidth(List<Double> sorted) {
        double mean = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = sorted.stream()
                .mapToDouble(value -> {
                    double delta = value - mean;
                    return delta * delta;
                })
                .average()
                .orElse(0.0);
        return 3.5 * Math.sqrt(variance) / Math.cbrt(sorted.size());
    }

    private static double percentile(List<Double> sorted, double percentile) {
        if (sorted.size() == 1) {
            return sorted.getFirst();
        }
        double position = percentile * (sorted.size() - 1);
        int lowerIndex = (int) Math.floor(position);
        int upperIndex = (int) Math.ceil(position);
        if (lowerIndex == upperIndex) {
            return sorted.get(lowerIndex);
        }
        double lower = sorted.get(lowerIndex);
        double upper = sorted.get(upperIndex);
        return lower + ((upper - lower) * (position - lowerIndex));
    }

    private static Instant nonNullInstant(Instant instant) {
        return instant == null ? Instant.EPOCH : instant;
    }
}
