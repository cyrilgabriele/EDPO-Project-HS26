package ch.unisg.cryptoflow.marketpartialbookingestion.adapter.in.binance;

import ch.unisg.cryptoflow.events.RawOrderBookDepthEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BinancePartialDepthEventMapperTests {

    private final BinancePartialDepthEventMapper mapper = new BinancePartialDepthEventMapper(new ObjectMapper());

    @Test
    void mapsCombinedStreamPayloadToRawOrderBookDepthEvent() throws Exception {
        RawOrderBookDepthEvent event = mapper.map("""
                {
                  "stream": "btcusdt@depth20@100ms",
                  "data": {
                    "e": "depthUpdate",
                    "E": 1777803305000,
                    "T": 1777803304995,
                    "s": "BTCUSDT",
                    "U": 100,
                    "u": 105,
                    "pu": 99,
                    "b": [["65000.10", "1.25"]],
                    "a": [["65001.20", "0.50"], ["65002.30", "2.00"]]
                  }
                }
                """);

        assertThat(event).isNotNull();
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.symbol()).isEqualTo("BTCUSDT");
        assertThat(event.eventTime()).isEqualTo(Instant.ofEpochMilli(1777803305000L));
        assertThat(event.transactionTime()).isEqualTo(Instant.ofEpochMilli(1777803304995L));
        assertThat(event.firstUpdateId()).isEqualTo(100L);
        assertThat(event.finalUpdateId()).isEqualTo(105L);
        assertThat(event.previousFinalUpdateId()).isEqualTo(99L);
        assertThat(event.bids()).hasSize(1);
        assertThat(event.bids().getFirst().price()).isEqualByComparingTo("65000.10");
        assertThat(event.asks()).hasSize(2);
        assertThat(event.asks().getFirst().quantity()).isEqualByComparingTo("0.50");
        assertThat(event.receivedAt()).isNotNull();
    }

    @Test
    void returnsNullForPayloadWithoutSymbol() throws Exception {
        assertThat(mapper.map("""
                {"data":{"E":1777803305000,"b":[],"a":[]}}
                """)).isNull();
    }
}
