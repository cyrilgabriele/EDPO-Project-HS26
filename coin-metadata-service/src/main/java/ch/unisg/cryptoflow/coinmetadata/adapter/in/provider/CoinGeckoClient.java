package ch.unisg.cryptoflow.coinmetadata.adapter.in.provider;

import ch.unisg.cryptoflow.coinmetadata.config.CoinMetadataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CoinGeckoClient implements CoinMetadataProvider {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoClient.class);

    private final RestClient http;
    private final CoinMetadataProperties props;

    public CoinGeckoClient(CoinMetadataProperties props) {
        this.props = props;
        this.http = RestClient.builder().baseUrl(props.provider().url()).build();
    }

    @Override
    public List<CoinMetadataFetch> fetchAll() {
        if (props.symbols() == null || props.symbols().isEmpty()) {
            return Collections.emptyList();
        }
        String ids = props.symbols().stream()
                .map(CoinMetadataProperties.SymbolMapping::coingecko)
                .distinct()
                .collect(Collectors.joining(","));

        log.debug("Fetching coin metadata: vs={} ids={}", props.provider().vsCurrency(), ids);

        List<CoinGeckoMarketEntry> response = http.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("vs_currency", props.provider().vsCurrency())
                        .queryParam("ids", ids)
                        .queryParam("order", "market_cap_desc")
                        .queryParam("per_page", "50")
                        .queryParam("page", "1")
                        .queryParam("sparkline", "false")
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null) {
            log.warn("CoinGecko returned a null body");
            return Collections.emptyList();
        }

        Map<String, CoinGeckoMarketEntry> byId = response.stream()
                .collect(Collectors.toMap(CoinGeckoMarketEntry::id, e -> e, (a, b) -> a));

        List<CoinMetadataFetch> out = new ArrayList<>(byId.size());
        for (CoinGeckoMarketEntry entry : byId.values()) {
            out.add(new CoinMetadataFetch(
                    entry.id(),
                    entry.name(),
                    entry.image(),
                    entry.marketCapRank(),
                    null,
                    null,
                    null));
        }
        return out;
    }
}
