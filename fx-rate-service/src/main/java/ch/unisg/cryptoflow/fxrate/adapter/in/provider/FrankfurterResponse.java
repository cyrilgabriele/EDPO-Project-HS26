package ch.unisg.cryptoflow.fxrate.adapter.in.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FrankfurterResponse(
        BigDecimal amount,
        String base,
        LocalDate date,
        Map<String, BigDecimal> rates) {
}
