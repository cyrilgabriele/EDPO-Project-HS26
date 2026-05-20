package ch.unisg.cryptoflow.transaction.domain;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory replica of {@code user.display-currency} keyed by userId.
 * Fallback to USD when the user has not been seen yet.
 *
 * <p>{@link #isReady()} returns true once the first Kafka replay record has
 * been delivered, signalling the cache is warm enough to serve quotes. Callers
 * should return 503 while the cache is cold rather than silently serving USD.
 */
@Component
public class DisplayCurrencyCache {

    public static final String DEFAULT = "USD";

    private final Map<String, String> byUserId = new ConcurrentHashMap<>();
    private volatile boolean ready = false;

    public void update(String userId, String displayCurrency) {
        byUserId.put(userId, displayCurrency.toUpperCase());
        ready = true;
    }

    public boolean isReady() {
        return ready;
    }

    public String getOrDefault(String userId) {
        return byUserId.getOrDefault(userId, DEFAULT);
    }
}
