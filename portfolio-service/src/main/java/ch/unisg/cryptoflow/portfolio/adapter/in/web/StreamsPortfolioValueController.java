package ch.unisg.cryptoflow.portfolio.adapter.in.web;

import ch.unisg.cryptoflow.events.avro.PortfolioValue;
import ch.unisg.cryptoflow.events.avro.PositionValue;
import ch.unisg.cryptoflow.portfolio.streams.PortfolioValueStoreReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Interactive-query endpoint for scope-04. Reads the latest
 * {@link PortfolioValue} from the local materialised state store maintained
 * by {@code PortfolioValuationStreamConfig}.
 *
 * <p>Kept distinct from the existing {@code /portfolios/{userId}/value}
 * endpoint (Postgres-backed) so both can be exercised side-by-side for the
 * state-store-vs-Postgres comparison story in the project report.
 */
@RestController
@RequestMapping("/portfolios")
public class StreamsPortfolioValueController {

    private final PortfolioValueStoreReader reader;

    public StreamsPortfolioValueController(PortfolioValueStoreReader reader, ObjectMapper objectMapper) {
        this.reader = reader;
    }

    @GetMapping("/{userId}/streams-value")
    public ResponseEntity<Object> getStreamsValue(@PathVariable String userId) {
        return reader.findByUserId(userId)
                .<ResponseEntity<Object>>map(value -> ResponseEntity.ok(toResponse(value)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> toResponse(PortfolioValue value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", value.getUserId());
        body.put("totalUsdt", value.getTotalUsdt());
        body.put("asOf", value.getAsOf().toString());
        List<Map<String, Object>> breakdown = value.getBreakdown().stream()
                .map(this::positionToMap)
                .toList();
        body.put("breakdown", breakdown);
        return body;
    }

    private Map<String, Object> positionToMap(PositionValue pv) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("symbol", pv.getSymbol());
        map.put("quantity", pv.getQuantity());
        map.put("valueUsdt", pv.getValueUsdt());
        return map;
    }
}
