package ch.unisg.cryptoflow.fxrate.application;

import ch.unisg.cryptoflow.fxrate.adapter.in.provider.FxRateFetch;
import ch.unisg.cryptoflow.fxrate.config.FxRateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Event Filter (catalog 25): drops FX fetches that are malformed, off-base, or older than
 * the configured validity window before they reach the translator.
 */
@Component
public class FxRateValidator {

    private static final Logger log = LoggerFactory.getLogger(FxRateValidator.class);

    private final FxRateProperties props;

    public FxRateValidator(FxRateProperties props) {
        this.props = props;
    }

    public boolean isValid(FxRateFetch fetch) {
        if (fetch == null) {
            log.warn("FX fetch is null");
            return false;
        }
        if (fetch.baseCurrency() == null || !fetch.baseCurrency().equalsIgnoreCase(props.baseCurrency())) {
            log.warn("FX base mismatch: expected={}, got={}", props.baseCurrency(), fetch.baseCurrency());
            return false;
        }
        if (fetch.rates() == null || fetch.rates().isEmpty()) {
            log.warn("FX fetch has no rates");
            return false;
        }
        for (var entry : fetch.rates().entrySet()) {
            BigDecimal rate = entry.getValue();
            if (rate == null || rate.signum() <= 0) {
                log.warn("Invalid rate for {}: {}", entry.getKey(), rate);
                return false;
            }
        }
        if (fetch.validFromAt() == null) {
            log.warn("FX fetch missing validFromAt");
            return false;
        }
        Duration age = Duration.between(fetch.validFromAt(), Instant.now());
        if (age.compareTo(props.maxResponseAge()) > 0) {
            log.warn("FX fetch too old: age={}, max={}", age, props.maxResponseAge());
            return false;
        }
        return true;
    }
}
