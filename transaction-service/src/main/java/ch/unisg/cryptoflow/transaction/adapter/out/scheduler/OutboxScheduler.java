package ch.unisg.cryptoflow.transaction.adapter.out.scheduler;

import ch.unisg.cryptoflow.events.OrderApprovedEvent;
import ch.unisg.cryptoflow.transaction.adapter.out.kafka.OrderApprovedEventProducer;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.OutboxEventEntity;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.SpringDataOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Orphan recovery: publishes any outbox rows that were not consumed by
 * {@link ch.unisg.cryptoflow.transaction.adapter.in.camunda.PublishOrderApprovedWorker}
 * (e.g. JVM crash between approveOrderWorker completing and the Send Task executing).
 * Runs every 5 minutes; only targets rows older than 5 minutes (ADR-0014).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final SpringDataOutboxEventRepository outboxRepository;
    private final OrderApprovedEventProducer orderApprovedEventProducer;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "PT5M")
    @Transactional
    public void publishOrphans() {
        Instant threshold = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<OutboxEventEntity> orphans =
                outboxRepository.findByPublishedAtIsNullAndCreatedAtBefore(threshold);

        if (orphans.isEmpty()) return;

        log.warn("OutboxScheduler: found {} unpublished outbox rows older than 5 min", orphans.size());

        for (OutboxEventEntity row : orphans) {
            try {
                OrderApprovedEvent event = objectMapper.readValue(row.getPayload(), OrderApprovedEvent.class);
                orderApprovedEventProducer.publish(event);
                row.markPublished(Instant.now());
                log.info("OutboxScheduler: published orphaned event transactionId={}", row.getTransactionId());
            } catch (Exception e) {
                log.error("OutboxScheduler: failed to publish outbox row id={} transactionId={}",
                        row.getId(), row.getTransactionId(), e);
            }
        }
    }
}
