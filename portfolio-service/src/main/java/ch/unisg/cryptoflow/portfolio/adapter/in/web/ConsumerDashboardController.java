package ch.unisg.cryptoflow.portfolio.adapter.in.web;

import ch.unisg.cryptoflow.portfolio.adapter.out.persistence.PortfolioRepository;
import ch.unisg.cryptoflow.portfolio.domain.DisplayCurrencyCache;
import ch.unisg.cryptoflow.portfolio.domain.FxRateCache;
import ch.unisg.cryptoflow.portfolio.domain.LocalPriceCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Exposes dashboard data for the static demo page at /. Returns metadata about
 * the ECST pattern, the local price cache snapshot, the local FX cache
 * snapshot, and all portfolios with their current holdings valued both in
 * USDT and in each user's Display Currency.
 */
@RestController
@RequestMapping("/api/dashboard")
public class ConsumerDashboardController {

    private final LocalPriceCache localPriceCache;
    private final FxRateCache fxRateCache;
    private final DisplayCurrencyCache displayCurrencyCache;
    private final PortfolioRepository portfolioRepository;
    private final String topic;
    private final String groupId;

    public ConsumerDashboardController(
            LocalPriceCache localPriceCache,
            FxRateCache fxRateCache,
            DisplayCurrencyCache displayCurrencyCache,
            PortfolioRepository portfolioRepository,
            @Value("${crypto.kafka.topic.price-raw}") String topic,
            @Value("${spring.kafka.consumer.group-id}") String groupId) {
        this.localPriceCache = localPriceCache;
        this.fxRateCache = fxRateCache;
        this.displayCurrencyCache = displayCurrencyCache;
        this.portfolioRepository = portfolioRepository;
        this.topic = topic;
        this.groupId = groupId;
    }

    @GetMapping
    public Map<String, Object> dashboard() {
        List<Map<String, Object>> portfolios = portfolioRepository.findAll().stream()
                .map(portfolio -> {
                    String dc = displayCurrencyCache.getOrDefault(portfolio.getUserId());
                    Optional<BigDecimal> fxRate = fxRateCache.getRate(dc);

                    List<Map<String, Object>> holdings = portfolio.getHoldings().stream()
                            .map(h -> {
                                Optional<BigDecimal> price = localPriceCache.getPrice(h.getSymbol());
                                Map<String, Object> holding = new LinkedHashMap<>();
                                holding.put("symbol", h.getSymbol());
                                holding.put("quantity", h.getQuantity().stripTrailingZeros().toPlainString());
                                holding.put("avgPrice", h.getAveragePurchasePrice().toPlainString());
                                price.ifPresentOrElse(
                                        p -> {
                                            BigDecimal usdtValue = h.getQuantity().multiply(p);
                                            holding.put("currentPrice", p.toPlainString());
                                            holding.put("currentValueUsdt",
                                                    usdtValue.setScale(2, RoundingMode.HALF_UP).toPlainString());
                                            fxRate.ifPresent(rate -> {
                                                holding.put("currentPriceDisplay",
                                                        p.multiply(rate).setScale(2, RoundingMode.HALF_UP).toPlainString());
                                                holding.put("currentValueDisplay",
                                                        usdtValue.multiply(rate).setScale(2, RoundingMode.HALF_UP).toPlainString());
                                            });
                                        },
                                        () -> {
                                            holding.put("currentPrice", "");
                                            holding.put("currentValueUsdt", "");
                                        }
                                );
                                holding.put("addedAt", h.getAddedAt() != null ? h.getAddedAt().toString() : "");
                                return holding;
                            })
                            .toList();

                    BigDecimal totalUsdt = holdings.stream()
                            .map(h -> (String) h.get("currentValueUsdt"))
                            .filter(v -> v != null && !v.isEmpty())
                            .map(BigDecimal::new)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("userId", portfolio.getUserId());
                    p.put("userName", portfolio.getUserName() != null ? portfolio.getUserName() : "");
                    p.put("displayCurrency", dc);
                    fxRate.ifPresent(rate -> p.put("fxRate", rate.toPlainString()));
                    p.put("holdings", holdings);
                    p.put("totalValueUsdt", totalUsdt.compareTo(BigDecimal.ZERO) > 0
                            ? totalUsdt.setScale(2, RoundingMode.HALF_UP).toPlainString() : "");
                    fxRate.ifPresent(rate -> {
                        BigDecimal totalDisplay = totalUsdt.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                        p.put("totalValueDisplay", totalDisplay.toPlainString());
                    });
                    return p;
                })
                .toList();

        Map<String, String> fxRatesView = new LinkedHashMap<>();
        fxRateCache.snapshot().forEach((k, v) -> fxRatesView.put(k, v.toPlainString()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "portfolio-service");
        response.put("pattern", "Event-Carried State Transfer (ECST)");
        response.put("topic", topic);
        response.put("groupId", groupId);
        response.put("cacheSize", localPriceCache.getAllPrices().size());
        response.put("prices", localPriceCache.getAllPrices());
        response.put("fxRates", fxRatesView);
        response.put("portfolios", portfolios);
        return response;
    }
}
