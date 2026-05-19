package ch.unisg.cryptoflow.transaction.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory USDT price replica per symbol, updated from {@code crypto.price.raw}.
 * Feeds the buy-time quote endpoint without a synchronous call to market-data-service.
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

    public Map<String, BigDecimal> snapshot() {
        return Map.copyOf(prices);
    }
}
