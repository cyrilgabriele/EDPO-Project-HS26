package ch.unisg.cryptoflow.portfolio.adapter.in.kafka;

import ch.unisg.cryptoflow.events.OrderApprovedEvent;
import ch.unisg.cryptoflow.portfolio.application.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link OrderApprovedEvent} published by transaction-service (ECST).
 * Upserts the portfolio holding with a weighted-average purchase price.
 * Self-contained: owns its own transaction boundary, no ack back to orchestrator.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderApprovedEventConsumer {

    private final PortfolioService portfolioService;

    @KafkaListener(
            topics = "${crypto.kafka.topic.order-approved}",
            containerFactory = "orderApprovedKafkaListenerContainerFactory"
    )
    public void onOrderApproved(OrderApprovedEvent event) {
        if (event == null || event.userId() == null) {
            log.warn("Received invalid OrderApprovedEvent: {}", event);
            return;
        }
        log.info("Processing OrderApprovedEvent transactionId={} userId={} symbol={} amount={} price={}",
                event.transactionId(), event.userId(), event.symbol(), event.amount(), event.matchedPrice());

        portfolioService.upsertHolding(event.transactionId(), event.userId(), event.symbol(), event.amount(), event.matchedPrice());
    }
}
