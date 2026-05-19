package ch.unisg.cryptoflow.portfolio.streams;

import ch.unisg.cryptoflow.events.OrderApprovedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SignedQuantityTest {

    @Test
    void returnsPositiveAmountForBuyApproval() {
        OrderApprovedEvent buy = new OrderApprovedEvent(
                "tx-1", "u-1", "BTCUSDT",
                new BigDecimal("0.5"), new BigDecimal("60000.00"),
                Instant.parse("2026-05-20T10:00:00Z"));

        assertThat(SignedQuantity.of(buy)).isEqualByComparingTo("0.5");
    }

    @Test
    void zeroWhenAmountIsNull() {
        OrderApprovedEvent malformed = new OrderApprovedEvent(
                "tx-2", "u-1", "BTCUSDT",
                null, new BigDecimal("60000.00"),
                Instant.parse("2026-05-20T10:00:00Z"));

        assertThat(SignedQuantity.of(malformed)).isEqualByComparingTo("0");
    }
}
