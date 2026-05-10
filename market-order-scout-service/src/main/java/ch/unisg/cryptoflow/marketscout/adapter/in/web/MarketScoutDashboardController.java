package ch.unisg.cryptoflow.marketscout.adapter.in.web;

import ch.unisg.cryptoflow.marketscout.domain.ScoutDashboardStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class MarketScoutDashboardController {

    public static final int REFRESH_INTERVAL_MILLIS = 3_000;

    private final ScoutDashboardStatsReader statsReader;
    private final String rawTopic;
    private final String askQuoteTopic;
    private final String matchableAskTopic;
    private final String askOpportunityTopic;
    private final String scoutSummaryTopic;

    public MarketScoutDashboardController(
            ScoutDashboardStatsReader statsReader,
            @Value("${crypto.kafka.topic.scout-raw}") String rawTopic,
            @Value("${crypto.kafka.topic.scout-ask-quotes}") String askQuoteTopic,
            @Value("${crypto.kafka.topic.scout-matchable-asks}") String matchableAskTopic,
            @Value("${crypto.kafka.topic.scout-ask-opportunities}") String askOpportunityTopic,
            @Value("${crypto.kafka.topic.scout-window-summary}") String scoutSummaryTopic) {
        this.statsReader = statsReader;
        this.rawTopic = rawTopic;
        this.askQuoteTopic = askQuoteTopic;
        this.matchableAskTopic = matchableAskTopic;
        this.askOpportunityTopic = askOpportunityTopic;
        this.scoutSummaryTopic = scoutSummaryTopic;
    }

    @GetMapping
    public Map<String, Object> dashboard() {
        Map<String, Object> topics = new LinkedHashMap<>();
        topics.put("raw", rawTopic);
        topics.put("askQuotes", askQuoteTopic);
        topics.put("matchableAsks", matchableAskTopic);
        topics.put("askOpportunities", askOpportunityTopic);
        topics.put("windowSummary", scoutSummaryTopic);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "market-order-scout-service");
        response.put("pattern", "Topology-only Kafka Streams dashboard state");
        response.put("refreshIntervalMillis", REFRESH_INTERVAL_MILLIS);
        response.put("topics", topics);
        response.put("symbols", statsReader.allStats().stream()
                .map(MarketScoutDashboardController::symbolStats)
                .toList());
        return response;
    }

    private static Map<String, Object> symbolStats(ScoutDashboardStats stats) {
        Map<String, Object> moments = new LinkedHashMap<>();
        moments.put("mean", stats.mean());
        moments.put("variance", stats.variance());
        moments.put("skewness", stats.skewness());
        moments.put("kurtosis", stats.kurtosis());

        Map<String, Object> symbol = new LinkedHashMap<>();
        symbol.put("symbol", stats.symbol());
        symbol.put("count", stats.count());
        symbol.put("moments", moments);
        symbol.put("histogramBins", stats.histogramBins());
        symbol.put("samples", stats.samples());
        return symbol;
    }
}
