package ch.unisg.cryptoflow.marketpartialbookingestion.adapter.in.binance;

import ch.unisg.cryptoflow.events.OrderBookLevel;
import ch.unisg.cryptoflow.events.RawOrderBookDepthEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class BinancePartialDepthEventMapper {

    private final ObjectMapper objectMapper;

    public BinancePartialDepthEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RawOrderBookDepthEvent map(String payload) throws java.io.IOException {
        JsonNode root = objectMapper.readTree(payload);
        JsonNode eventNode = root.has("data") ? root.path("data") : root;

        String symbol = eventNode.path("s").asText(null);
        if (symbol == null || symbol.isBlank()) {
            return null;
        }

        return new RawOrderBookDepthEvent(
                UUID.randomUUID().toString(),
                symbol,
                instantFromEpochMillis(eventNode.path("E").asLong(0)),
                instantFromEpochMillis(eventNode.path("T").asLong(0)),
                eventNode.path("U").asLong(0),
                eventNode.path("u").asLong(0),
                eventNode.path("pu").asLong(0),
                parseLevels(eventNode.path("b")),
                parseLevels(eventNode.path("a")),
                Instant.now()
        );
    }

    private List<OrderBookLevel> parseLevels(JsonNode levelsNode) {
        List<OrderBookLevel> levels = new ArrayList<>();
        if (!levelsNode.isArray()) {
            return levels;
        }

        for (JsonNode levelNode : levelsNode) {
            if (levelNode.isArray() && levelNode.size() >= 2) {
                levels.add(new OrderBookLevel(
                        new BigDecimal(levelNode.get(0).asText()),
                        new BigDecimal(levelNode.get(1).asText())
                ));
            }
        }
        return levels;
    }

    private Instant instantFromEpochMillis(long epochMillis) {
        return epochMillis > 0 ? Instant.ofEpochMilli(epochMillis) : Instant.EPOCH;
    }
}
