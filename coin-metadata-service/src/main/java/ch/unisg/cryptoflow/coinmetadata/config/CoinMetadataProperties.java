package ch.unisg.cryptoflow.coinmetadata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "coin")
public record CoinMetadataProperties(
        Provider provider,
        Duration pollInterval,
        List<SymbolMapping> symbols) {

    public record Provider(String name, String url, String vsCurrency) {}

    public record SymbolMapping(String binance, String base, String quote, String coingecko) {}
}
