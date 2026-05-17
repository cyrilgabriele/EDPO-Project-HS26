package ch.unisg.cryptoflow.fxrate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "fx")
public record FxRateProperties(
        Provider provider,
        String baseCurrency,
        List<String> quoteCurrencies,
        Duration pollInterval,
        Duration maxResponseAge) {

    public record Provider(String name, String url) {}
}
