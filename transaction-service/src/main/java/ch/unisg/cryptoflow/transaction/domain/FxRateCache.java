package ch.unisg.cryptoflow.transaction.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory FX rate replica, fed from {@code reference.fx.rate}. Key is the
 * quote currency ISO-4217 code (USD as the implicit base maps to BigDecimal.ONE).
 */
@Component
public class FxRateCache {

    private final Map<String, BigDecimal> ratesByQuote = new ConcurrentHashMap<>();

    public FxRateCache() {
        ratesByQuote.put("USD", BigDecimal.ONE);
    }

    public void update(String quoteCurrency, BigDecimal rate) {
        ratesByQuote.put(quoteCurrency.toUpperCase(), rate);
    }

    public Optional<BigDecimal> getRate(String quoteCurrency) {
        return Optional.ofNullable(ratesByQuote.get(quoteCurrency.toUpperCase()));
    }
}
