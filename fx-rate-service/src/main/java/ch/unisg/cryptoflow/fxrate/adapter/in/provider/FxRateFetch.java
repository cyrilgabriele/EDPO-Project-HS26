package ch.unisg.cryptoflow.fxrate.adapter.in.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Provider-agnostic snapshot of one FX poll, before validation and translation.
 */
public record FxRateFetch(
        String baseCurrency,
        Instant validFromAt,
        Map<String, BigDecimal> rates,
        String source) {
}
