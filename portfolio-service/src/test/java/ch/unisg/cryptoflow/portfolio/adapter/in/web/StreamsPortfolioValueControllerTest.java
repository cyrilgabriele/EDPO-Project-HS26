package ch.unisg.cryptoflow.portfolio.adapter.in.web;

import ch.unisg.cryptoflow.events.avro.PortfolioValue;
import ch.unisg.cryptoflow.events.avro.PositionValue;
import ch.unisg.cryptoflow.portfolio.streams.PortfolioValueStoreReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StreamsPortfolioValueControllerTest {

    private PortfolioValueStoreReader reader;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reader = new FakePortfolioValueStoreReader();
        StreamsPortfolioValueController controller = new StreamsPortfolioValueController(
                reader, new ObjectMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void returns404WhenStoreHasNoEntryForUser() throws Exception {
        ((FakePortfolioValueStoreReader) reader).value = Optional.empty();

        mockMvc.perform(get("/portfolios/u-1/streams-value"))
                .andExpect(status().isNotFound());

        assertThat(((FakePortfolioValueStoreReader) reader).requestedUserId).isEqualTo("u-1");
    }

    @Test
    void returnsBreakdownAndTotalFromStore() throws Exception {
        PortfolioValue value = PortfolioValue.newBuilder()
                .setUserId("u-1")
                .setTotalUsdt(new BigDecimal("32000"))
                .setBreakdown(List.of(
                        PositionValue.newBuilder()
                                .setSymbol("BTCUSDT")
                                .setQuantity(new BigDecimal("0.5"))
                                .setValueUsdt(new BigDecimal("30000"))
                                .build(),
                        PositionValue.newBuilder()
                                .setSymbol("ETHUSDT")
                                .setQuantity(new BigDecimal("1"))
                                .setValueUsdt(new BigDecimal("2000"))
                                .build()))
                .setAsOf(Instant.parse("2026-05-20T10:00:00Z"))
                .build();
        ((FakePortfolioValueStoreReader) reader).value = Optional.of(value);

        mockMvc.perform(get("/portfolios/u-1/streams-value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u-1"))
                .andExpect(jsonPath("$.totalUsdt").value(32000))
                .andExpect(jsonPath("$.breakdown[0].symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.breakdown[1].symbol").value("ETHUSDT"));

        assertThat(((FakePortfolioValueStoreReader) reader).requestedUserId).isEqualTo("u-1");
    }

    private static final class FakePortfolioValueStoreReader extends PortfolioValueStoreReader {
        private Optional<PortfolioValue> value = Optional.empty();
        private String requestedUserId;

        private FakePortfolioValueStoreReader() {
            super(new org.springframework.beans.factory.ObjectProvider<
                    org.springframework.kafka.config.StreamsBuilderFactoryBean>() {});
        }

        @Override
        public Optional<PortfolioValue> findByUserId(String userId) {
            requestedUserId = userId;
            return value;
        }
    }
}
