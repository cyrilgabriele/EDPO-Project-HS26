package ch.unisg.cryptoflow.marketscout.domain;

import java.time.Instant;

public record ScoutDashboardSample(
        Instant windowStart,
        Instant windowEnd,
        double logMinAskPrice
) {
    public String windowId() {
        return windowStart + "|" + windowEnd;
    }
}
