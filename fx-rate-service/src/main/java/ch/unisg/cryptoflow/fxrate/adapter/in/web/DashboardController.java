package ch.unisg.cryptoflow.fxrate.adapter.in.web;

import ch.unisg.cryptoflow.fxrate.application.RecentFxRates;
import ch.unisg.cryptoflow.fxrate.config.FxRateProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes dashboard data for the static demo page at /. Returns service metadata
 * plus a ring-buffer of recently published FxRate events.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final RecentFxRates recent;
    private final FxRateProperties props;
    private final String topic;

    public DashboardController(
            RecentFxRates recent,
            FxRateProperties props,
            @Value("${reference.kafka.topic.fx-rate}") String topic) {
        this.recent = recent;
        this.props = props;
        this.topic = topic;
    }

    @GetMapping
    public Map<String, Object> dashboard() {
        return Map.of(
                "service", "fx-rate-service",
                "pattern", "Single-Event Processing",
                "topic", topic,
                "provider", Map.of(
                        "name", props.provider().name(),
                        "url", props.provider().url()),
                "baseCurrency", props.baseCurrency(),
                "quoteCurrencies", props.quoteCurrencies(),
                "pollInterval", props.pollInterval().toString(),
                "maxResponseAge", props.maxResponseAge().toString(),
                "recentEvents", recent.getRecent()
        );
    }
}
