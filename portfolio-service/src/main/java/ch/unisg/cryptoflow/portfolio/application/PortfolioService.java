package ch.unisg.cryptoflow.portfolio.application;

import ch.unisg.cryptoflow.portfolio.adapter.out.persistence.HoldingEntity;
import ch.unisg.cryptoflow.portfolio.adapter.out.persistence.PortfolioEntity;
import ch.unisg.cryptoflow.portfolio.adapter.out.persistence.PortfolioRepository;
import ch.unisg.cryptoflow.portfolio.domain.LocalPriceCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final LocalPriceCache localPriceCache;

    public PortfolioService(PortfolioRepository portfolioRepository,
                            LocalPriceCache localPriceCache) {
        this.portfolioRepository = portfolioRepository;
        this.localPriceCache = localPriceCache;
    }

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
    public Optional<BigDecimal> calculateTotalValue(String userId) {
        return portfolioRepository.findByUserId(userId)
                .flatMap(this::sumHoldingValues);
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
