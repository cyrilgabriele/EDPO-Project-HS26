package ch.unisg.cryptoflow.portfolio.domain;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory replica of user.display-currency, keyed by userId. Updated by the
 * Avro consumer of {@code user.display-currency} (compacted, see ADR-0028).
 *
 * <p>Read on the HTTP request path to convert portfolio valuations into the
 * user's chosen Display Currency without calling user-service.
 */
@Component
public class DisplayCurrencyCache {

    public static final String DEFAULT = "USD";

    private final Map<String, String> byUserId = new ConcurrentHashMap<>();

    public void update(String userId, String displayCurrency) {
        byUserId.put(userId, displayCurrency.toUpperCase());
    }

    /**
     * Returns the user's chosen Display Currency, falling back to USD if the
     * KTable has not yet replicated the user's row.
     */
    public String getOrDefault(String userId) {
        return byUserId.getOrDefault(userId, DEFAULT);
    }

    public Optional<String> get(String userId) {
        return Optional.ofNullable(byUserId.get(userId));
    }
}
