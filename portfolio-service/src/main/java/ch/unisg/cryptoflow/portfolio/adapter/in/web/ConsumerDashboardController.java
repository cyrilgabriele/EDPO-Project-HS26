package ch.unisg.cryptoflow.portfolio.adapter.in.web;

import ch.unisg.cryptoflow.portfolio.domain.LocalPriceCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes dashboard data for the static demo page at {@code /}.
 * Returns metadata about the ECST pattern and the current local price cache snapshot.
 */
@RestController
@RequestMapping("/api/dashboard")
public class ConsumerDashboardController {

    private final LocalPriceCache localPriceCache;
    private final String topic;
    private final String groupId;

    public ConsumerDashboardController(
            LocalPriceCache localPriceCache,
            @Value("${crypto.kafka.topic.price-raw}") String topic,
            @Value("${spring.kafka.consumer.group-id}") String groupId) {
        this.localPriceCache = localPriceCache;
        this.topic = topic;
        this.groupId = groupId;
    }

    @GetMapping
    public Map<String, Object> dashboard() {
        return Map.of(
                "service", "portfolio-service",
                "pattern", "Event-Carried State Transfer (ECST)",
                "topic", topic,
                "groupId", groupId,
                "cacheSize", localPriceCache.getAllPrices().size(),
                "prices", localPriceCache.getAllPrices()
        );
    }
}
