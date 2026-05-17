package ch.unisg.cryptoflow.coinmetadata.adapter.in.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinGeckoMarketEntry(
        String id,
        String symbol,
        String name,
        String image,
        @JsonProperty("market_cap_rank") Integer marketCapRank) {
}
