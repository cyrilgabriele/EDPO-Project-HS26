package ch.unisg.cryptoflow.marketscout.adapter.in.web;

import ch.unisg.cryptoflow.marketscout.adapter.in.kafka.MarketScoutTopology;
import ch.unisg.cryptoflow.marketscout.domain.ScoutDashboardStats;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class KafkaStreamsScoutDashboardStatsReader implements ScoutDashboardStatsReader {

    private final ObjectProvider<StreamsBuilderFactoryBean> streamsBuilderFactoryBean;

    public KafkaStreamsScoutDashboardStatsReader(ObjectProvider<StreamsBuilderFactoryBean> streamsBuilderFactoryBean) {
        this.streamsBuilderFactoryBean = streamsBuilderFactoryBean;
    }

    @Override
    public List<ScoutDashboardStats> allStats() {
        StreamsBuilderFactoryBean factoryBean = streamsBuilderFactoryBean.getIfAvailable();
        KafkaStreams kafkaStreams = factoryBean == null ? null : factoryBean.getKafkaStreams();
        if (kafkaStreams == null) {
            return List.of();
        }

        try {
            ReadOnlyKeyValueStore<String, ScoutDashboardStats> store = kafkaStreams.store(
                    StoreQueryParameters.fromNameAndType(
                            MarketScoutTopology.DASHBOARD_STATS_STORE,
                            QueryableStoreTypes.keyValueStore()));
            List<ScoutDashboardStats> stats = new ArrayList<>();
            try (var iterator = store.all()) {
                while (iterator.hasNext()) {
                    stats.add(iterator.next().value);
                }
            }
            stats.sort(Comparator.comparing(ScoutDashboardStats::symbol));
            return List.copyOf(stats);
        } catch (InvalidStateStoreException ignored) {
            return List.of();
        }
    }
}
