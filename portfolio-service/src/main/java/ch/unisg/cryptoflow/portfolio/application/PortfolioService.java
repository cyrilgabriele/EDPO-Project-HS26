package ch.unisg.cryptoflow.portfolio.application;

import ch.unisg.cryptoflow.portfolio.adapter.out.persistence.HoldingEntity;
import ch.unisg.cryptoflow.portfolio.adapter.out.persistence.PortfolioEntity;
import ch.unisg.cryptoflow.portfolio.adapter.out.persistence.PortfolioRepository;
import ch.unisg.cryptoflow.portfolio.adapter.out.persistence.ProcessedTransactionEntity;
import ch.unisg.cryptoflow.portfolio.adapter.out.persistence.ProcessedTransactionRepository;
import ch.unisg.cryptoflow.portfolio.domain.LocalPriceCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@Transactional
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final ProcessedTransactionRepository processedTransactionRepository;
    private final LocalPriceCache localPriceCache;

    public PortfolioService(PortfolioRepository portfolioRepository,
                            ProcessedTransactionRepository processedTransactionRepository,
                            LocalPriceCache localPriceCache) {
        this.portfolioRepository = portfolioRepository;
        this.processedTransactionRepository = processedTransactionRepository;
        this.localPriceCache = localPriceCache;
    }

    @Transactional(readOnly = true)
    public Optional<PortfolioEntity> getPortfolio(String userId) {
        return portfolioRepository.findByUserId(userId);
    }

    /**
     * Calculates the total portfolio value as {@code sum(quantity × cachedPrice)} per holding.
     *
     * @return the total value, or {@link Optional#empty()} if any holding symbol has no cached
     *         price yet (the consumer is still warming up). Returns {@link Optional#empty()} also
     *         when the portfolio does not exist.
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> calculateTotalValue(String userId) {
        return portfolioRepository.findByUserId(userId)
                .flatMap(this::sumHoldingValues);
    }

    public PortfolioCreationResult createPortfolioForUser(String userId, String userName) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(userName, "userName must not be null");
        return portfolioRepository.findByUserId(userId)
                .map(entity -> {
                    if (entity.getUserName() == null) {
                        entity.setUserName(userName);
                    }
                    return new PortfolioCreationResult(entity.getId(), false);
                })
                .orElseGet(() -> {
                    PortfolioEntity created = portfolioRepository.save(new PortfolioEntity(userId, userName));
                    return new PortfolioCreationResult(created.getId(), true);
                });
    }

    public boolean deletePortfolioForUser(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return portfolioRepository.findByUserId(userId)
                .map(entity -> {
                    portfolioRepository.delete(entity);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Idempotently upserts a holding for the given user after an order is approved.
     * The transactionId is recorded in processed_transaction; duplicate deliveries are silently skipped.
     * Creates the portfolio if it doesn't exist yet (auto-create on first trade).
     * Updates the weighted average purchase price when the symbol is already held.
     */
    public void upsertHolding(String transactionId, String userId, String symbol,
                              BigDecimal amount, BigDecimal price) {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(symbol, "symbol must not be null");

        try {
            processedTransactionRepository.saveAndFlush(
                    new ProcessedTransactionEntity(transactionId, Instant.now()));
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate OrderApprovedEvent transactionId={} — skipping", transactionId);
            return;
        }

        PortfolioEntity portfolio = portfolioRepository.findByUserId(userId)
                .orElseGet(() -> portfolioRepository.save(new PortfolioEntity(userId, null)));

        portfolio.getHoldings().stream()
                .filter(h -> h.getSymbol().equalsIgnoreCase(symbol))
                .findFirst()
                .ifPresentOrElse(
                        existing -> {
                            // Weighted average: (oldQty*oldAvg + newQty*newPrice) / (oldQty + newQty)
                            BigDecimal totalCost = existing.getQuantity()
                                    .multiply(existing.getAveragePurchasePrice())
                                    .add(amount.multiply(price));
                            BigDecimal totalQty = existing.getQuantity().add(amount);
                            existing.updateQuantityAndPrice(
                                    totalQty,
                                    totalCost.divide(totalQty, 8, RoundingMode.HALF_UP)
                            );
                        },
                        () -> portfolio.getHoldings().add(new HoldingEntity(symbol, amount, price, portfolio))
                );
    }

    private Optional<BigDecimal> sumHoldingValues(PortfolioEntity portfolio) {
        BigDecimal total = BigDecimal.ZERO;
        for (HoldingEntity holding : portfolio.getHoldings()) {
            Optional<BigDecimal> price = localPriceCache.getPrice(holding.getSymbol());
            if (price.isEmpty()) {
                return Optional.empty();
            }
            total = total.add(holding.getQuantity().multiply(price.get()));
        }
        return Optional.of(total);
    }
}
