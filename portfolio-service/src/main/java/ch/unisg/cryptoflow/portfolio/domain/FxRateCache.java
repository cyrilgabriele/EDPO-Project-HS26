package ch.unisg.cryptoflow.portfolio.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory FX rate replica, updated by the consumer of reference.fx.rate.
 *
 * <p>Materialises the compacted topic as a KTable-equivalent: key is the
 * quote currency ISO-4217 code (USD as base is implicit), value is the latest
 * rate as quote-per-1-USD. USD maps to BigDecimal.ONE.
 *
 * <p>This is the tracer-phase shape. Once scope 03 ships a
 * {@code crypto.price.localized} stream with prices pre-converted per currency,
 * portfolio-service can consume that instead and skip the multiplication here.
 */
@Component
public class FxRateCache {

    private final Map<String, BigDecimal> ratesByQuote = new ConcurrentHashMap<>();

    public FxRateCache() {
        // USD-as-base means 1 USD = 1 USD; never need an event to set this.
        ratesByQuote.put("USD", BigDecimal.ONE);
    }

    public void update(String quoteCurrency, BigDecimal rate) {
        ratesByQuote.put(quoteCurrency.toUpperCase(), rate);
    }

    public Optional<BigDecimal> getRate(String quoteCurrency) {
        return Optional.ofNullable(ratesByQuote.get(quoteCurrency.toUpperCase()));
    }

    public Map<String, BigDecimal> snapshot() {
        return Map.copyOf(ratesByQuote);
    }
}
