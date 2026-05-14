package ch.unisg.cryptoflow.transaction.adapter.in.kafka;

import ch.unisg.cryptoflow.transaction.adapter.out.camunda.OrderExecutedMessageSender;
import ch.unisg.cryptoflow.transaction.avro.OrderMatched;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderMatchedEventConsumer {

    private final OrderExecutedMessageSender orderExecutedMessageSender;

    @KafkaListener(
            topics = "${crypto.kafka.topic.order-matched}",
            groupId = "${spring.kafka.consumer.group-id}-order-matched-camunda",
            containerFactory = "orderMatchedKafkaListenerContainerFactory"
    )
    public void onOrderMatched(OrderMatched event) {
        if (event == null || event.getTransactionId() == null || event.getMatchedPrice() == null) {
            log.warn("Received null or malformed order matched event - skipping");
            return;
        }

        log.info("Consumed order matched event: transactionId={} askQuoteId={} matchedPrice={}",
                event.getTransactionId(), event.getAskQuoteId(), event.getMatchedPrice());
        orderExecutedMessageSender.sendPriceMatchedMessage(
                event.getTransactionId(),
                event.getSymbol(),
                event.getMatchedPrice());
    }
}
