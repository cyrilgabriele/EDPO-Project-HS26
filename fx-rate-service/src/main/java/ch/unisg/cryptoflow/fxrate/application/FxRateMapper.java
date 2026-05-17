package ch.unisg.cryptoflow.fxrate.application;

import ch.unisg.cryptoflow.events.avro.FxRate;
import ch.unisg.cryptoflow.fxrate.adapter.in.provider.FxRateFetch;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Event Translator (catalog 26): turns one provider response into one FxRate per
 * quote currency, normalising codes to upper case and scaling the rate to the
 * Avro decimal precision configured in shared-events/src/main/avro/FxRate.avsc.
 */
@Component
public class FxRateMapper {

    private static final int AVRO_DECIMAL_SCALE = 18;

    public List<FxRate> toEvents(FxRateFetch fetch) {
        Instant now = Instant.now();
        String base = fetch.baseCurrency().toUpperCase();
        List<FxRate> events = new ArrayList<>(fetch.rates().size());

        for (Map.Entry<String, BigDecimal> entry : fetch.rates().entrySet()) {
            String quote = entry.getKey().toUpperCase();
            BigDecimal rate = entry.getValue().setScale(AVRO_DECIMAL_SCALE, RoundingMode.HALF_UP);

            events.add(FxRate.newBuilder()
                    .setBase(base)
                    .setQuote(quote)
                    .setRate(rate)
                    .setSource(fetch.source())
                    .setFetchedAt(now)
                    .setValidFromAt(fetch.validFromAt())
                    .build());
        }
        return events;
    }
}
