package ch.unisg.cryptoflow.transaction.adapter.in.camunda;

import ch.unisg.cryptoflow.transaction.application.OrderMatchingService;
import ch.unisg.cryptoflow.transaction.application.port.out.UpdateTransactionPort;
import ch.unisg.cryptoflow.transaction.domain.TransactionStatus;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Handles the {@code rejectOrderWorker} job: marks the transaction as REJECTED
 * and removes any stale in-memory entry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RejectOrderWorker {

    private final UpdateTransactionPort updateTransactionPort;
    private final OrderMatchingService orderMatchingService;

    @JobWorker(type = "rejectOrderWorker", autoComplete = false)
    public void rejectOrder(JobClient client, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String transactionId = (String) variables.get("transactionId");

        log.info("Rejecting order transactionId={} (timeout expired)", transactionId);

        // 1. Persist REJECTED status
        updateTransactionPort.updateStatus(
                transactionId,
                TransactionStatus.REJECTED,
                Instant.now(),
                null
        );

        // 2. Clean up stale in-memory entry if still present
        orderMatchingService.removeOrder(transactionId);

        client.newCompleteCommand(job.getKey()).variables(variables).send().join();
    }
}
