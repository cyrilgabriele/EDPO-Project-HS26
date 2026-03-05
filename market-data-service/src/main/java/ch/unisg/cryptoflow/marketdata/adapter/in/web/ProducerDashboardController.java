package ch.unisg.cryptoflow.marketdata.adapter.in.web;

import ch.unisg.cryptoflow.marketdata.application.EventLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Exposes dashboard data for the static demo page at {@code /}.
 * Returns metadata about the Event Notification pattern and a live ring-buffer
 * of recently published events.
 */
@RestController
@RequestMapping("/api/dashboard")
public class ProducerDashboardController {

    private final EventLog eventLog;
    private final String topic;
    private final List<String> symbols;

    public ProducerDashboardController(
            EventLog eventLog,
            @Value("${crypto.kafka.topic.price-raw}") String topic,
            @Value("${binance.symbols}") List<String> symbols) {
        this.eventLog = eventLog;
        this.topic = topic;
        this.symbols = symbols;
    }

    @GetMapping
    public Map<String, Object> dashboard() {
        return Map.of(
                "service", "market-data-service",
                "pattern", "Event Notification",
                "topic", topic,
                "symbols", symbols,
                "recentEvents", eventLog.getRecent()
        );
    }
}
