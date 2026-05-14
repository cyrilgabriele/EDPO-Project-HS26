package ch.unisg.cryptoflow.transaction.adapter.in.camunda;

import ch.unisg.cryptoflow.transaction.adapter.in.camunda.job.OrderProcessingVariables;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.ConfirmedUserRepository;
import ch.unisg.cryptoflow.transaction.application.port.out.SaveTransactionPort;
import ch.unisg.cryptoflow.transaction.avro.BuyBid;
import ch.unisg.cryptoflow.transaction.domain.TransactionRecord;
import ch.unisg.cryptoflow.transaction.domain.TransactionStatus;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Incoming Camunda adapter – handles the {@code placeOrderWorker} job.
 *
 * <p>Validates input, generates a transactionId, persists the order as PENDING, and publishes
 * it as a symbol-keyed buy bid for the matching topology. Throws a BPMN error {@code INVALID_ORDER} for
 * deterministic validation failures so the process loops back to the order form.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PlaceOrderWorker {

    private final SaveTransactionPort saveTransactionPort;
    private final ConfirmedUserRepository confirmedUserRepository;
    private final KafkaTemplate<String, BuyBid> buyBidKafkaTemplate;
    private final ZeebeClient zeebeClient;

    @Value("${crypto.kafka.topic.buy-bids}")
    private String buyBidTopic;

    @JobWorker(type = "placeOrderWorker", autoComplete = false)
    public void placeOrder(JobClient client, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        OrderProcessingVariables vars = OrderProcessingVariables.fromMap(variables);

        String validationError = validate(vars);
        if (validationError != null) {
            log.warn("Order validation failed: {}", validationError);
            // Set at process instance scope so the variable is visible when the
            // boundary event routes back to the user task form.
            zeebeClient.newSetVariablesCommand(job.getProcessInstanceKey())
                    .variables(Map.of("validationError", validationError))
                    .send().join();
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("INVALID_ORDER")
                    .errorMessage(validationError)
                    .send().join();
            return;
        }

        String transactionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String symbol = normalizeSymbol(vars.symbol());
        BigDecimal amount = normalize(new BigDecimal(vars.amount()));
        BigDecimal price = normalize(new BigDecimal(vars.price()));
        log.debug("{} | transactionId: {}", vars, transactionId);

        saveTransactionPort.save(new TransactionRecord(
                transactionId,
                vars.userId(),
                symbol,
                amount,
                price,
                TransactionStatus.PENDING,
                now,
                null,
                null
        ));

        BuyBid bid = BuyBid.newBuilder()
                .setTransactionId(transactionId)
                .setUserId(vars.userId())
                .setSymbol(symbol)
                .setBidQuantity(amount)
                .setBidPrice(price)
                .setCreatedAt(now)
                .build();
        buyBidKafkaTemplate.send(buyBidTopic, symbol, bid);

        variables.put("transactionId", transactionId);
        variables.put("userId", vars.userId());
        variables.put("symbol", symbol);
        variables.put("validationError", "");  // clear any previous validation error
        client.newCompleteCommand(job.getKey()).variables(variables).send().join();
    }

    private String validate(OrderProcessingVariables vars) {
        if (vars.userId() == null || vars.userId().isBlank()) {
            return "User ID is required";
        }
        if (!confirmedUserRepository.existsById(vars.userId())) {
            return "User not found or registration not confirmed. Please complete onboarding first.";
        }
        if (vars.symbol() == null || vars.symbol().isBlank()) {
            return "Symbol is required";
        }
        if (vars.amount() == null || vars.amount().isBlank()) {
            return "Amount is required";
        }
        if (vars.price() == null || vars.price().isBlank()) {
            return "Price is required";
        }
        try {
            if (new BigDecimal(vars.amount()).compareTo(BigDecimal.ZERO) <= 0) {
                return "Amount must be greater than zero";
            }
            if (new BigDecimal(vars.price()).compareTo(BigDecimal.ZERO) <= 0) {
                return "Price must be greater than zero";
            }
        } catch (NumberFormatException e) {
            return "Amount and price must be valid numbers";
        }
        return null;
    }

    private static String normalizeSymbol(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value.setScale(18, RoundingMode.HALF_UP);
    }
}
