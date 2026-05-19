package ch.unisg.cryptoflow.coinmetadata.adapter.in.provider;

import java.util.List;

/**
 * Provider-agnostic snapshot of one coin's metadata, before mapping to Avro.
 */
public record CoinMetadataFetch(
        String coinGeckoId,
        String name,
        String imageUrl,
        Integer marketCapRank,
        List<String> categories,
        String description,
        String homepageUrl) {
}
