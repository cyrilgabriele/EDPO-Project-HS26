package ch.unisg.cryptoflow.portfolio.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory price replica updated by the Kafka consumer.
 *
 * <p>This component implements the <strong>Event-carried State Transfer</strong>
 * pattern: the portfolio service never calls {@code market-data-service} directly.
 * Instead it maintains its own local copy of the latest price per symbol and
 * uses it to answer REST queries – even when the producer is temporarily offline.
 *
 * <p>Thread-safe: updated by the Kafka listener thread, read by the HTTP request
 * threads concurrently.
 */
@Component
public class LocalPriceCache {

    private final Map<String, BigDecimal> prices = new ConcurrentHashMap<>();

    public void updatePrice(String symbol, BigDecimal price) {
        prices.put(symbol, price);
    }

    public Optional<BigDecimal> getPrice(String symbol) {
        return Optional.ofNullable(prices.get(symbol));
    }

    public Map<String, BigDecimal> getAllPrices() {
        return Map.copyOf(prices);
    }
}
