package ch.unisg.cryptoflow.transaction.adapter.in.camunda;

import ch.unisg.cryptoflow.transaction.adapter.in.camunda.job.OrderProcessingVariables;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.ConfirmedUserRepository;
import ch.unisg.cryptoflow.transaction.application.port.out.SaveTransactionPort;
import ch.unisg.cryptoflow.transaction.avro.BuyBid;
import ch.unisg.cryptoflow.transaction.domain.DisplayCurrencyCache;
import ch.unisg.cryptoflow.transaction.domain.FxRateCache;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Incoming Camunda adapter for the {@code placeOrderWorker} job.
 *
 * <p>The price the user enters on the form is interpreted in their
 * Display Currency. The worker converts it to USDT using the latest FX rate
 * from the local KTable replica, persists both values for audit, and publishes
 * the USDT-denominated {@link BuyBid} for the matching topology.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PlaceOrderWorker {

    private final SaveTransactionPort saveTransactionPort;
    private final ConfirmedUserRepository confirmedUserRepository;
    private final KafkaTemplate<String, BuyBid> buyBidKafkaTemplate;
    private final ZeebeClient zeebeClient;
    private final DisplayCurrencyCache displayCurrencyCache;
    private final FxRateCache fxRateCache;

    @Value("${crypto.kafka.topic.buy-bids}")
    private String buyBidTopic;

    @JobWorker(type = "placeOrderWorker", autoComplete = false)
    public void placeOrder(JobClient client, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        OrderProcessingVariables vars = OrderProcessingVariables.fromMap(variables);

        String validationError = validate(vars);
        if (validationError != null) {
            failWithValidation(client, job, validationError);
            return;
        }

        String userId = vars.userId();
        String displayCurrency = displayCurrencyCache.getOrDefault(userId);
        Optional<BigDecimal> fxRate = fxRateCache.getRate(displayCurrency);
        if (fxRate.isEmpty()) {
            String msg = "FX rate for " + displayCurrency + " not yet cached, please retry";
            failWithValidation(client, job, msg);
            return;
        }

        String transactionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String symbol = normalizeSymbol(vars.symbol());
        BigDecimal amount = normalize(new BigDecimal(vars.amount()));
        BigDecimal priceDisplay = normalize(new BigDecimal(vars.price()));
        BigDecimal priceUsdt = normalize(priceDisplay.divide(fxRate.get(), 18, RoundingMode.HALF_UP));

        log.info("Order placed: userId={} symbol={} amount={} priceDisplay={} {} fxRate={} priceUsdt={}",
                userId, symbol, amount, priceDisplay, displayCurrency, fxRate.get(), priceUsdt);

        saveTransactionPort.save(new TransactionRecord(
                transactionId,
                userId,
                symbol,
                amount,
                priceUsdt,
                priceDisplay,
                displayCurrency,
                TransactionStatus.PENDING,
                now,
                null,
                null
        ));

        BuyBid bid = BuyBid.newBuilder()
                .setTransactionId(transactionId)
                .setUserId(userId)
                .setSymbol(symbol)
                .setBidQuantity(amount)
                .setBidPrice(priceUsdt)
                .setCreatedAt(now)
                .build();
        buyBidKafkaTemplate.send(buyBidTopic, symbol, bid);

        variables.put("transactionId", transactionId);
        variables.put("userId", userId);
        variables.put("symbol", symbol);
        variables.put("displayCurrency", displayCurrency);
        variables.put("priceUsdt", priceUsdt.toPlainString());
        variables.put("validationError", "");
        client.newCompleteCommand(job.getKey()).variables(variables).send().join();
    }

    private void failWithValidation(JobClient client, ActivatedJob job, String message) {
        log.warn("Order validation failed: {}", message);
        zeebeClient.newSetVariablesCommand(job.getProcessInstanceKey())
                .variables(Map.of("validationError", message))
                .send().join();
        client.newThrowErrorCommand(job.getKey())
                .errorCode("INVALID_ORDER")
                .errorMessage(message)
                .send().join();
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
