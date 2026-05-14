package ch.unisg.cryptoflow.transaction.config;

import ch.unisg.cryptoflow.events.avro.MatchableAsk;
import ch.unisg.cryptoflow.events.avro.SpecificAvroSerde;
import ch.unisg.cryptoflow.transaction.avro.BuyBid;
import ch.unisg.cryptoflow.transaction.avro.OrderMatched;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.Map;

@Configuration
public class MatchingAuditKafkaConfig {

    @Bean
    public ConsumerFactory<String, BuyBid> matchingAuditBuyBidConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId) {
        return avroConsumerFactory(
                bootstrapServers,
                groupId + "-matching-audit-buy-bids",
                new SpecificAvroSerde<>(BuyBid.class, BuyBid.getClassSchema()));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BuyBid>
    matchingAuditBuyBidKafkaListenerContainerFactory(
            ConsumerFactory<String, BuyBid> matchingAuditBuyBidConsumerFactory) {
        return listenerFactory(matchingAuditBuyBidConsumerFactory);
    }

    @Bean
    public ConsumerFactory<String, MatchableAsk> matchingAuditMatchableAskConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId) {
        return avroConsumerFactory(
                bootstrapServers,
                groupId + "-matching-audit-matchable-asks",
                new SpecificAvroSerde<>(MatchableAsk.class, MatchableAsk.getClassSchema()));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MatchableAsk>
    matchingAuditMatchableAskKafkaListenerContainerFactory(
            ConsumerFactory<String, MatchableAsk> matchingAuditMatchableAskConsumerFactory) {
        return listenerFactory(matchingAuditMatchableAskConsumerFactory);
    }

    @Bean
    public ConsumerFactory<String, OrderMatched> matchingAuditOrderMatchedConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId) {
        return avroConsumerFactory(
                bootstrapServers,
                groupId + "-matching-audit-order-matches",
                new SpecificAvroSerde<>(OrderMatched.class, OrderMatched.getClassSchema()));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderMatched>
    matchingAuditOrderMatchedKafkaListenerContainerFactory(
            ConsumerFactory<String, OrderMatched> matchingAuditOrderMatchedConsumerFactory) {
        return listenerFactory(matchingAuditOrderMatchedConsumerFactory);
    }

    private static <T extends org.apache.avro.specific.SpecificRecordBase> ConsumerFactory<String, T> avroConsumerFactory(
            String bootstrapServers,
            String groupId,
            SpecificAvroSerde<T> serde) {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(properties, new StringDeserializer(), serde.deserializer());
    }

    private static <T> ConcurrentKafkaListenerContainerFactory<String, T> listenerFactory(
            ConsumerFactory<String, T> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }
}
