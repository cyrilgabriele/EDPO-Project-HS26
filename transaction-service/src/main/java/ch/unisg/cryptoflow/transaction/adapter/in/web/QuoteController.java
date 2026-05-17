package ch.unisg.cryptoflow.transaction.adapter.in.web;

import ch.unisg.cryptoflow.transaction.domain.DisplayCurrencyCache;
import ch.unisg.cryptoflow.transaction.domain.FxRateCache;
import ch.unisg.cryptoflow.transaction.domain.LocalPriceCache;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Buy-time quote endpoint. Returns the latest USDT price for a symbol and
 * the converted value in the user's Display Currency, both served from local
 * KTable-style caches without a synchronous call to market-data-service,
 * fx-rate-service or user-service.
 */
@RestController
@RequestMapping("/quote")
public class QuoteController {

    private final LocalPriceCache prices;
    private final FxRateCache fxRates;
    private final DisplayCurrencyCache displayCurrencies;

    public QuoteController(LocalPriceCache prices,
                           FxRateCache fxRates,
                           DisplayCurrencyCache displayCurrencies) {
        this.prices = prices;
        this.fxRates = fxRates;
        this.displayCurrencies = displayCurrencies;
    }

    @GetMapping("/{userId}/{symbol}")
    public ResponseEntity<Object> quote(@PathVariable String userId, @PathVariable String symbol) {
        String normalisedSymbol = symbol.toUpperCase();
        Optional<BigDecimal> priceUsdt = prices.getPrice(normalisedSymbol);
        if (priceUsdt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "No price cached for symbol " + normalisedSymbol + " yet"));
        }
        String dc = displayCurrencies.getOrDefault(userId);
        Optional<BigDecimal> fxRate = fxRates.getRate(dc);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("symbol", normalisedSymbol);
        body.put("priceUsdt", priceUsdt.get());
        body.put("displayCurrency", dc);
        fxRate.ifPresent(rate -> {
            body.put("fxRate", rate);
            body.put("priceDisplay", priceUsdt.get().multiply(rate).setScale(8, RoundingMode.HALF_UP));
        });
        if (fxRate.isEmpty()) {
            body.put("warning", "FX rate for " + dc + " not yet cached");
        }
        return ResponseEntity.ok(body);
    }
}
