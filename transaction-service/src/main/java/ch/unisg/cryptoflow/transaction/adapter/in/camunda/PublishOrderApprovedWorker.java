package ch.unisg.cryptoflow.transaction.adapter.in.camunda;

import ch.unisg.cryptoflow.events.OrderApprovedEvent;
import ch.unisg.cryptoflow.transaction.adapter.out.kafka.OrderApprovedEventProducer;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.OutboxEventEntity;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.SpringDataOutboxEventRepository;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Handles the {@code publishOrderApprovedWorker} Send Task:
 * publishes {@link OrderApprovedEvent} to Kafka and marks the outbox row as published.
 * Zeebe retries this job automatically on failure, keeping the main path durable
 * without a separate reconciliation scheduler for this step (ADR-0014, ADR-0015).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublishOrderApprovedWorker {

    private final OrderApprovedEventProducer orderApprovedEventProducer;
    private final SpringDataOutboxEventRepository outboxRepository;

    @JobWorker(type = "publishOrderApprovedWorker", autoComplete = false)
    @Transactional
    public void publishOrderApproved(JobClient client, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String transactionId = (String) variables.get("transactionId");
        String userId        = (String) variables.get("userId");
        String symbol        = (String) variables.get("symbol");
        String amount        = (String) variables.get("amount");
        String matchedPrice  = (String) variables.get("matchedPrice");

        // Dev/test hook: set forceError=OUTBOX_EVENT_NOT_FOUND on the process instance
        // via Camunda Operate's variable editor to trigger the error boundary without
        // corrupting DB state.
        if ("OUTBOX_EVENT_NOT_FOUND".equals(variables.get("forceError"))) {
            log.warn("[DEV] forceError=OUTBOX_EVENT_NOT_FOUND set on process instance — throwing BPMN error");
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("OUTBOX_EVENT_NOT_FOUND")
                    .errorMessage("Forced error for testing")
                    .send().join();
            return;
        }

        log.info("Publishing OrderApprovedEvent transactionId={}", transactionId);

        OrderApprovedEvent event = new OrderApprovedEvent(
                transactionId, userId, symbol,
                new BigDecimal(amount), new BigDecimal(matchedPrice),
                Instant.now());

        orderApprovedEventProducer.publish(event);

        OutboxEventEntity outboxEvent = outboxRepository.findByTransactionId(transactionId)
                .orElse(null);
        if (outboxEvent == null) {
            log.error("Outbox row not found for transactionId={} — cannot mark published", transactionId);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("OUTBOX_EVENT_NOT_FOUND")
                    .errorMessage("Outbox event not found for transactionId: " + transactionId)
                    .send().join();
            return;
        }
        outboxEvent.markPublished(Instant.now());

        client.newCompleteCommand(job.getKey()).variables(variables).send().join();
    }
}
