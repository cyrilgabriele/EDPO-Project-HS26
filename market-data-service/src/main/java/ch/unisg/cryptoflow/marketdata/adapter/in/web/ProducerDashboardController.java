package ch.unisg.cryptoflow.marketdata.adapter.in.web;

import ch.unisg.cryptoflow.marketdata.application.EventLog;
import ch.unisg.cryptoflow.marketdata.streams.RecentOhlcBars;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes dashboard data for the static demo page at /. Returns metadata about
 * the Event Notification pattern, a ring-buffer of recently published price
 * events, and the latest closed OHLC bar per (symbol, interval) emitted by the
 * scope-05 streams app.
 */
@RestController
@RequestMapping("/api/dashboard")
public class ProducerDashboardController {

    private final EventLog eventLog;
    private final RecentOhlcBars recentOhlcBars;
    private final String topic;
    private final String ohlc1mTopic;
    private final String ohlc5mTopic;
    private final String ohlc1hTopic;
    private final List<String> symbols;

    public ProducerDashboardController(
            EventLog eventLog,
            RecentOhlcBars recentOhlcBars,
            @Value("${crypto.kafka.topic.price-raw}") String topic,
            @Value("${crypto.kafka.topic.ohlc-1m}") String ohlc1mTopic,
            @Value("${crypto.kafka.topic.ohlc-5m}") String ohlc5mTopic,
            @Value("${crypto.kafka.topic.ohlc-1h}") String ohlc1hTopic,
            @Value("${binance.symbols}") List<String> symbols) {
        this.eventLog = eventLog;
        this.recentOhlcBars = recentOhlcBars;
        this.topic = topic;
        this.ohlc1mTopic = ohlc1mTopic;
        this.ohlc5mTopic = ohlc5mTopic;
        this.ohlc1hTopic = ohlc1hTopic;
        this.symbols = symbols;
    }

    @GetMapping
    public Map<String, Object> dashboard() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "market-data-service");
        body.put("pattern", "Event Notification + Tumbling Windows");
        body.put("topic", topic);
        body.put("symbols", symbols);
        body.put("recentEvents", eventLog.getRecent());
        body.put("ohlcTopics", Map.of(
                "oneMinute", ohlc1mTopic,
                "fiveMinutes", ohlc5mTopic,
                "oneHour", ohlc1hTopic));
        body.put("ohlcBars", recentOhlcBars.snapshot());
        return body;
    }
}
