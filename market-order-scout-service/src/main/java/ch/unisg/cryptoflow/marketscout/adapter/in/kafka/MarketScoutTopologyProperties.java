package ch.unisg.cryptoflow.marketscout.adapter.in.kafka;

import java.math.BigDecimal;
import java.time.Duration;

public record MarketScoutTopologyProperties(
        String rawTopic,
        String askQuoteTopic,
        String matchableAskTopic,
        String askOpportunityTopic,
        String scoutSummaryTopic,
        BigDecimal askThreshold,
        Duration summaryWindow,
        String sourceVenue
) {
}
