package ch.unisg.cryptoflow.transaction.config;

import ch.unisg.cryptoflow.events.avro.SpecificAvroSerde;
import ch.unisg.cryptoflow.transaction.adapter.in.kafka.TransactionMatchingTopology;
import ch.unisg.cryptoflow.transaction.adapter.in.kafka.TransactionMatchingTopologyProperties;
import ch.unisg.cryptoflow.transaction.avro.BuyBid;
import ch.unisg.cryptoflow.transaction.avro.OrderMatched;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafkaStreams
@ConditionalOnProperty(name = "crypto.transaction-matching.topology.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionMatchingKafkaConfig {

    @Bean(name = org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration transactionKafkaStreamsConfiguration(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.application.name}") String applicationName) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationName + "-matching-topology");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, org.apache.kafka.common.serialization.Serdes.StringSerde.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaStreamsConfiguration(properties);
    }

    @Bean
    public TransactionMatchingTopologyProperties transactionMatchingTopologyProperties(
            @Value("${crypto.kafka.topic.buy-bids}") String buyBidTopic,
            @Value("${crypto.kafka.topic.matchable-asks}") String matchableAskTopic,
            @Value("${crypto.kafka.topic.order-matched}") String orderMatchedTopic,
            @Value("${crypto.transaction-matching.validity-window}") Duration validityWindow,
            @Value("${crypto.transaction-matching.grace-period}") Duration gracePeriod) {
        return new TransactionMatchingTopologyProperties(
                buyBidTopic, matchableAskTopic, orderMatchedTopic, validityWindow, gracePeriod);
    }

    @Bean
    public KStream<String, OrderMatched> transactionMatchingStream(
            StreamsBuilder streamsBuilder,
            TransactionMatchingTopologyProperties properties) {
        return TransactionMatchingTopology.build(streamsBuilder, properties);
    }

    @Bean
    public ProducerFactory<String, BuyBid> buyBidProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> properties = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        SpecificAvroSerde<BuyBid> buyBidSerde = new SpecificAvroSerde<>(BuyBid.class, BuyBid.getClassSchema());
        return new DefaultKafkaProducerFactory<>(properties, new StringSerializer(), buyBidSerde.serializer());
    }

    @Bean
    public KafkaTemplate<String, BuyBid> buyBidKafkaTemplate(ProducerFactory<String, BuyBid> buyBidProducerFactory) {
        return new KafkaTemplate<>(buyBidProducerFactory);
    }

    @Bean
    public org.springframework.kafka.core.ConsumerFactory<String, OrderMatched> orderMatchedConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId) {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, groupId + "-order-matched",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        SpecificAvroSerde<OrderMatched> orderMatchedSerde =
                new SpecificAvroSerde<>(OrderMatched.class, OrderMatched.getClassSchema());
        return new org.springframework.kafka.core.DefaultKafkaConsumerFactory<>(
                properties, new StringDeserializer(), orderMatchedSerde.deserializer());
    }

    @Bean
    public org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<String, OrderMatched>
    orderMatchedKafkaListenerContainerFactory(
            org.springframework.kafka.core.ConsumerFactory<String, OrderMatched> orderMatchedConsumerFactory) {
        org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<String, OrderMatched> factory =
                new org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderMatchedConsumerFactory);
        return factory;
    }
}
