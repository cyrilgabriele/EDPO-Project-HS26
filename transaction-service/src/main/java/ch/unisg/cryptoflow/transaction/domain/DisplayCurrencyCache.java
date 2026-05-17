package ch.unisg.cryptoflow.transaction.domain;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory replica of {@code user.display-currency} keyed by userId.
 * Fallback to USD when the user has not been seen yet.
 */
@Component
public class DisplayCurrencyCache {

    public static final String DEFAULT = "USD";

    private final Map<String, String> byUserId = new ConcurrentHashMap<>();

    public void update(String userId, String displayCurrency) {
        byUserId.put(userId, displayCurrency.toUpperCase());
    }

    public String getOrDefault(String userId) {
        return byUserId.getOrDefault(userId, DEFAULT);
    }
}
