package ch.unisg.cryptoflow.fxrate.adapter.in.provider;

import ch.unisg.cryptoflow.fxrate.config.FxRateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalTime;
import java.time.ZoneOffset;

@Component
public class FrankfurterClient implements FxRateProvider {

    private static final Logger log = LoggerFactory.getLogger(FrankfurterClient.class);

    private final RestClient http;
    private final FxRateProperties props;

    public FrankfurterClient(FxRateProperties props) {
        this.props = props;
        this.http = RestClient.builder().baseUrl(props.provider().url()).build();
    }

    @Override
    public FxRateFetch fetch() {
        String symbols = String.join(",", props.quoteCurrencies());
        log.debug("Fetching FX rates: base={} symbols={}", props.baseCurrency(), symbols);

        FrankfurterResponse response = http.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("base", props.baseCurrency())
                        .queryParam("symbols", symbols)
                        .build())
                .retrieve()
                .body(FrankfurterResponse.class);

        if (response == null) {
            log.warn("FX provider returned a null body");
            return null;
        }

        // Frankfurter publishes a date, not a timestamp. Treat midday UTC as the validity anchor.
        var validFromAt = response.date().atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC);

        return new FxRateFetch(
                response.base(),
                validFromAt,
                response.rates(),
                props.provider().name());
    }
}
