package ch.unisg.cryptoflow.marketscout.domain;

public record ScoutDashboardHistogramBin(
        double lowerBound,
        double upperBound,
        long count
) {
}
