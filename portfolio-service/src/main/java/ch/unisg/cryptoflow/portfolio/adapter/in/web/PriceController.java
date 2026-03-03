package ch.unisg.cryptoflow.portfolio.adapter.in.web;

import ch.unisg.cryptoflow.portfolio.domain.LocalPriceCache;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * REST controller exposing the local price cache (ECST read model).
 */
@RestController
@RequestMapping("/prices")
public class PriceController {

    private final LocalPriceCache localPriceCache;

    public PriceController(LocalPriceCache localPriceCache) {
        this.localPriceCache = localPriceCache;
    }

    /**
     * Returns the latest cached price for a given symbol.
     * Responds 503 if the price has not yet been received from Kafka.
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<Object> getPrice(@PathVariable String symbol) {
        String upperSymbol = symbol.toUpperCase();
        return localPriceCache.getPrice(upperSymbol)
                .<ResponseEntity<Object>>map(price ->
                        ResponseEntity.ok(Map.of("symbol", upperSymbol, "price", price)))
                .orElseGet(() ->
                        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body(Map.of("error",
                                        "Price not yet cached for symbol: " + upperSymbol +
                                        ". The market-data-service may still be warming up.")));
    }

    /** Returns the full local price cache for all known symbols. */
    @GetMapping
    public ResponseEntity<Map<String, BigDecimal>> getAllPrices() {
        return ResponseEntity.ok(localPriceCache.getAllPrices());
    }
}
