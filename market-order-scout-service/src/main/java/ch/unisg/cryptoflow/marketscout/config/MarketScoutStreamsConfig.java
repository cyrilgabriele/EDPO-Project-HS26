package ch.unisg.cryptoflow.marketscout.config;

import ch.unisg.cryptoflow.events.RawOrderBookDepthEvent;
import ch.unisg.cryptoflow.marketscout.adapter.in.kafka.MarketScoutTopology;
import ch.unisg.cryptoflow.marketscout.adapter.in.kafka.MarketScoutTopologyProperties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.KafkaStreamsConfiguration;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafkaStreams
@ConditionalOnProperty(name = "crypto.market-scout.topology.enabled", havingValue = "true", matchIfMissing = true)
public class MarketScoutStreamsConfig {

    @Bean(name = org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kafkaStreamsConfiguration(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.application.name}") String applicationName) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationName + "-topology");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaStreamsConfiguration(properties);
    }

    @Bean
    public Clock marketScoutClock() {
        return Clock.systemUTC();
    }

    @Bean
    public MarketScoutTopologyProperties marketScoutTopologyProperties(
            @Value("${crypto.kafka.topic.scout-raw}") String rawTopic,
            @Value("${crypto.kafka.topic.scout-ask-quotes}") String askQuoteTopic,
            @Value("${crypto.kafka.topic.scout-matchable-asks}") String matchableAskTopic,
            @Value("${crypto.kafka.topic.scout-ask-opportunities}") String askOpportunityTopic,
            @Value("${crypto.kafka.topic.scout-window-summary}") String scoutSummaryTopic,
            @Value("${crypto.market-scout.ask-threshold}") BigDecimal askThreshold,
            @Value("${crypto.market-scout.window-size}") Duration summaryWindow,
            @Value("${crypto.market-scout.source-venue}") String sourceVenue) {
        return new MarketScoutTopologyProperties(
                rawTopic,
                askQuoteTopic,
                matchableAskTopic,
                askOpportunityTopic,
                scoutSummaryTopic,
                askThreshold,
                summaryWindow,
                sourceVenue);
    }

    @Bean
    public KStream<String, RawOrderBookDepthEvent> marketScoutStream(
            StreamsBuilder streamsBuilder,
            MarketScoutTopologyProperties properties,
            Clock marketScoutClock) {
        return MarketScoutTopology.build(streamsBuilder, properties, marketScoutClock);
    }
}
