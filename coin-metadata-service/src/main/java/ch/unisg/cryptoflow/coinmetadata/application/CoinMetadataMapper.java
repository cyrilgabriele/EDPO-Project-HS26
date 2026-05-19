package ch.unisg.cryptoflow.coinmetadata.application;

import ch.unisg.cryptoflow.coinmetadata.adapter.in.provider.CoinMetadataFetch;
import ch.unisg.cryptoflow.coinmetadata.config.CoinMetadataProperties;
import ch.unisg.cryptoflow.events.avro.CoinMetadata;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Translates each provider fetch into one or more CoinMetadata records, one per
 * Binance symbol that maps to the same CoinGecko id. The symbol-mapping table
 * is the source of truth for which trading pairs are tracked.
 */
@Component
public class CoinMetadataMapper {

    private final CoinMetadataProperties props;

    public CoinMetadataMapper(CoinMetadataProperties props) {
        this.props = props;
    }

    public List<CoinMetadata> toEvents(List<CoinMetadataFetch> fetches) {
        Instant now = Instant.now();
        Map<String, CoinMetadataFetch> byCoinGeckoId = fetches.stream()
                .collect(Collectors.toMap(CoinMetadataFetch::coinGeckoId, Function.identity(), (a, b) -> a));

        List<CoinMetadata> events = new ArrayList<>(props.symbols().size());
        for (CoinMetadataProperties.SymbolMapping mapping : props.symbols()) {
            CoinMetadataFetch fetch = byCoinGeckoId.get(mapping.coingecko());
            if (fetch == null) continue;
            events.add(CoinMetadata.newBuilder()
                    .setSymbol(mapping.binance())
                    .setBaseAsset(mapping.base())
                    .setQuoteAsset(mapping.quote())
                    .setCoinGeckoId(fetch.coinGeckoId())
                    .setName(fetch.name())
                    .setImageUrl(fetch.imageUrl())
                    .setMarketCapRank(fetch.marketCapRank())
                    .setCategories(fetch.categories())
                    .setDescription(fetch.description())
                    .setHomepageUrl(fetch.homepageUrl())
                    .setFetchedAt(now)
                    .build());
        }
        return events;
    }
}
