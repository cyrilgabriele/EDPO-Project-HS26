package ch.unisg.cryptoflow.transaction.adapter.in.camunda;

import ch.unisg.cryptoflow.events.OrderApprovedEvent;
import ch.unisg.cryptoflow.transaction.application.port.out.ApproveTransactionPort;
import ch.unisg.cryptoflow.transaction.domain.TransactionNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the {@code approveOrderWorker} job: atomically marks the transaction APPROVED
 * and inserts an outbox event row in the same DB transaction (ADR-0014).
 * Kafka publication is delegated to {@link PublishOrderApprovedWorker} (next BPMN step).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApproveOrderWorker {

    private final ApproveTransactionPort approveTransactionPort;
    private final ObjectMapper objectMapper;

    @Value("${crypto.kafka.topic.order-approved}")
    private String orderApprovedTopic;

    @JobWorker(type = "approveOrderWorker", autoComplete = false)
    public void approveOrder(JobClient client, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String transactionId = (String) variables.get("transactionId");
        String userId        = (String) variables.get("userId");
        String symbol        = (String) variables.get("symbol");
        String amount        = (String) variables.get("amount");
        String matchedPrice  = (String) variables.get("matchedPrice");

        if (transactionId == null || transactionId.isBlank()) {
            log.error("approveOrderWorker received job with missing transactionId");
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("TRANSACTION_NOT_FOUND")
                    .errorMessage("transactionId is missing from job variables")
                    .send().join();
            return;
        }

        // Dev/test hook: set forceError=TRANSACTION_NOT_FOUND on the process instance
        // via Camunda Operate's variable editor to trigger the error boundary without
        // corrupting DB state.
        if ("TRANSACTION_NOT_FOUND".equals(variables.get("forceError"))) {
            log.warn("[DEV] forceError=TRANSACTION_NOT_FOUND set on process instance — throwing BPMN error");
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("TRANSACTION_NOT_FOUND")
                    .errorMessage("Forced error for testing")
                    .send().join();
            return;
        }

        Instant now = Instant.now();
        log.info("Approving order transactionId={} symbol={} matchedPrice={}", transactionId, symbol, matchedPrice);

        OrderApprovedEvent event = new OrderApprovedEvent(
                transactionId, userId, symbol,
                new BigDecimal(amount), new BigDecimal(matchedPrice), now);

        String outboxPayload;
        try {
            outboxPayload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise OrderApprovedEvent for outbox", e);
        }

        try {
            // Atomically: UPDATE transaction_record status=APPROVED + INSERT outbox row
            approveTransactionPort.approveWithOutbox(
                    transactionId, now, new BigDecimal(matchedPrice),
                    UUID.randomUUID().toString(), orderApprovedTopic, outboxPayload);
        } catch (TransactionNotFoundException e) {
            log.error("Cannot approve order — transaction record not found: {}", transactionId);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("TRANSACTION_NOT_FOUND")
                    .errorMessage(e.getMessage())
                    .send().join();
            return;
        }

        client.newCompleteCommand(job.getKey()).variables(variables).send().join();
    }
}
