package ch.unisg.cryptoflow.transaction.adapter.in.kafka;

import ch.unisg.cryptoflow.events.avro.MatchableAsk;
import ch.unisg.cryptoflow.transaction.avro.BuyBid;
import ch.unisg.cryptoflow.transaction.avro.OrderMatched;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class MatchingAuditConsumer {

    private final MatchingAuditService matchingAuditService;

    @KafkaListener(
            topics = "${crypto.kafka.topic.buy-bids}",
            groupId = "${spring.kafka.consumer.group-id}-matching-audit-buy-bids",
            containerFactory = "matchingAuditBuyBidKafkaListenerContainerFactory"
    )
    public void onBuyBid(ConsumerRecord<String, BuyBid> record) {
        BuyBid bid = record.value();
        if (bid == null || bid.getTransactionId() == null) {
            log.warn("Received null or malformed buy bid audit record - skipping");
            return;
        }
        matchingAuditService.recordBuyBid(bid, record.topic(), record.partition(), record.offset());
    }

    @KafkaListener(
            topics = "${crypto.kafka.topic.matchable-asks}",
            groupId = "${spring.kafka.consumer.group-id}-matching-audit-matchable-asks",
            containerFactory = "matchingAuditMatchableAskKafkaListenerContainerFactory"
    )
    public void onMatchableAsk(ConsumerRecord<String, MatchableAsk> record) {
        MatchableAsk ask = record.value();
        if (ask == null || ask.getAskQuoteId() == null) {
            log.warn("Received null or malformed matchable ask audit record - skipping");
            return;
        }
        matchingAuditService.recordMatchableAsk(ask, record.topic(), record.partition(), record.offset());
    }

    @KafkaListener(
            topics = "${crypto.kafka.topic.order-matched}",
            groupId = "${spring.kafka.consumer.group-id}-matching-audit-order-matches",
            containerFactory = "matchingAuditOrderMatchedKafkaListenerContainerFactory"
    )
    public void onOrderMatched(ConsumerRecord<String, OrderMatched> record) {
        OrderMatched match = record.value();
        if (match == null || match.getTransactionId() == null) {
            log.warn("Received null or malformed order matched audit record - skipping");
            return;
        }
        matchingAuditService.recordOrderMatched(match, record.topic(), record.partition(), record.offset());
    }
}
