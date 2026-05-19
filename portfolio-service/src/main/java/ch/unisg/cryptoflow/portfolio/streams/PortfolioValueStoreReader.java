package ch.unisg.cryptoflow.portfolio.streams;

import ch.unisg.cryptoflow.events.avro.PortfolioValue;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Reads the latest {@link PortfolioValue} per user from the materialised
 * {@code portfolio-value-store}. Single-instance assumption in dev — for a
 * multi-instance deployment add metadata-lookup-with-redirect using
 * {@code KafkaStreams.queryMetadataForKey(...)}.
 */
@Component
public class PortfolioValueStoreReader {

    private final ObjectProvider<StreamsBuilderFactoryBean> factoryBeanProvider;

    public PortfolioValueStoreReader(ObjectProvider<StreamsBuilderFactoryBean> factoryBeanProvider) {
        this.factoryBeanProvider = factoryBeanProvider;
    }

    public Optional<PortfolioValue> findByUserId(String userId) {
        StreamsBuilderFactoryBean factoryBean = factoryBeanProvider.getIfAvailable();
        KafkaStreams streams = factoryBean == null ? null : factoryBean.getKafkaStreams();
        if (streams == null) {
            return Optional.empty();
        }
        try {
            ReadOnlyKeyValueStore<String, PortfolioValue> store = streams.store(
                    StoreQueryParameters.fromNameAndType(
                            PortfolioValuationStreamConfig.PORTFOLIO_VALUE_STORE,
                            QueryableStoreTypes.keyValueStore()));
            return Optional.ofNullable(store.get(userId));
        } catch (InvalidStateStoreException ignored) {
            return Optional.empty();
        }
    }
}
