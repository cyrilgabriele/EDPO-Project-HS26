package ch.unisg.cryptoflow.portfolio.adapter.in.web;

import ch.unisg.cryptoflow.portfolio.adapter.out.persistence.HoldingEntity;
import ch.unisg.cryptoflow.portfolio.adapter.out.persistence.PortfolioEntity;
import ch.unisg.cryptoflow.portfolio.application.PortfolioService;
import ch.unisg.cryptoflow.portfolio.domain.DisplayCurrencyCache;
import ch.unisg.cryptoflow.portfolio.domain.FxRateCache;
import ch.unisg.cryptoflow.portfolio.domain.LocalPriceCache;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CQRS read-side REST controller for portfolio queries.
 *
 * <p>All valuations are computed from local caches (ECST, see ADR-0002):
 * {@link LocalPriceCache} for USDT prices, {@link FxRateCache} for USD-quoted FX,
 * and {@link DisplayCurrencyCache} for the user's chosen Display Currency.
 * No synchronous calls are made to market-data-service, fx-rate-service or user-service.
 *
 * <p>Tracer-phase note (per ADR-0030): the long-term shape consumes
 * {@code crypto.price.localized} directly so {@code prices[displayCurrency]} replaces
 * the price-times-FX multiplication below. That swap is pending scope 03.
 */
@RestController
@RequestMapping("/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final LocalPriceCache localPriceCache;
    private final FxRateCache fxRateCache;
    private final DisplayCurrencyCache displayCurrencyCache;

    public PortfolioController(PortfolioService portfolioService,
                               LocalPriceCache localPriceCache,
                               FxRateCache fxRateCache,
                               DisplayCurrencyCache displayCurrencyCache) {
        this.portfolioService = portfolioService;
        this.localPriceCache = localPriceCache;
        this.fxRateCache = fxRateCache;
        this.displayCurrencyCache = displayCurrencyCache;
    }

    /** Returns the portfolio with current per-holding valuation in USDT and Display Currency. */
    @GetMapping("/{userId}")
    public ResponseEntity<Object> getPortfolio(@PathVariable String userId) {
        return portfolioService.getPortfolio(userId)
                .<ResponseEntity<Object>>map(p -> ResponseEntity.ok(toResponse(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Returns the total portfolio value in USDT and in the user's Display Currency. */
    @GetMapping("/{userId}/value")
    public ResponseEntity<Object> getPortfolioValue(@PathVariable String userId) {
        if (portfolioService.getPortfolio(userId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PortfolioService.ValuationResult result = portfolioService.valuation(userId);
        if (result.totalUsdt().isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error",
                            "One or more symbol prices are not yet cached. " +
                            "The market-data-service may still be warming up."));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("totalValueUsdt", result.totalUsdt().get());
        body.put("displayCurrency", result.displayCurrency());
        result.fxRate().ifPresent(rate -> body.put("fxRate", rate));
        result.convertedTotal().ifPresentOrElse(
                ct -> body.put("totalValueDisplay", ct),
                () -> body.put("totalValueDisplayWarning",
                        "FX rate for " + result.displayCurrency() + " not yet cached"));
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> toResponse(PortfolioEntity portfolio) {
        String dc = displayCurrencyCache.getOrDefault(portfolio.getUserId());
        Optional<BigDecimal> fxRate = fxRateCache.getRate(dc);

        List<Map<String, Object>> holdings = portfolio.getHoldings().stream()
                .map(h -> holdingToMap(h, dc, fxRate))
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", portfolio.getUserId());
        response.put("userName", portfolio.getUserName());
        response.put("displayCurrency", dc);
        fxRate.ifPresent(rate -> response.put("fxRate", rate));
        response.put("holdings", holdings);
        return response;
    }

    private Map<String, Object> holdingToMap(HoldingEntity holding, String dc, Optional<BigDecimal> fxRate) {
        Optional<BigDecimal> price = localPriceCache.getPrice(holding.getSymbol());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("symbol", holding.getSymbol());
        map.put("quantity", holding.getQuantity());
        map.put("averagePurchasePrice", holding.getAveragePurchasePrice());
        price.ifPresent(p -> {
            BigDecimal usdtValue = holding.getQuantity().multiply(p);
            map.put("currentPrice", p);
            map.put("currentValueUsdt", usdtValue);
            fxRate.ifPresent(rate -> map.put(
                    "currentValueDisplay",
                    usdtValue.multiply(rate).setScale(8, RoundingMode.HALF_UP)));
        });
        return map;
    }
}
