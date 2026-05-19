package ch.unisg.cryptoflow.coinmetadata.adapter.in.provider;

import java.util.List;

public interface CoinMetadataProvider {

    List<CoinMetadataFetch> fetchAll();
}
