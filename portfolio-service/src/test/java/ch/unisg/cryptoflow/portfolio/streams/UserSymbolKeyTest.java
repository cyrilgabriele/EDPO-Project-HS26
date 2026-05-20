package ch.unisg.cryptoflow.portfolio.streams;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserSymbolKeyTest {

    @Test
    void encodesUserAndSymbolWithPipeDelimiter() {
        assertThat(UserSymbolKey.encode("u-1", "BTCUSDT")).isEqualTo("u-1|BTCUSDT");
    }

    @Test
    void roundTripsThroughDecode() {
        String encoded = UserSymbolKey.encode("u-42", "ETHUSDT");
        UserSymbolKey decoded = UserSymbolKey.decode(encoded);
        assertThat(decoded.userId()).isEqualTo("u-42");
        assertThat(decoded.symbol()).isEqualTo("ETHUSDT");
    }

    @Test
    void uppercasesSymbolOnEncode() {
        assertThat(UserSymbolKey.encode("u", "btcusdt")).isEqualTo("u|BTCUSDT");
    }

    @Test
    void rejectsUserIdContainingDelimiter() {
        assertThatThrownBy(() -> UserSymbolKey.encode("u|1", "BTCUSDT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeRejectsMissingDelimiter() {
        assertThatThrownBy(() -> UserSymbolKey.decode("nodelimiter"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankUserId() {
        assertThatThrownBy(() -> UserSymbolKey.encode("   ", "BTCUSDT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankSymbol() {
        assertThatThrownBy(() -> UserSymbolKey.encode("u-1", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSymbolContainingDelimiter() {
        assertThatThrownBy(() -> UserSymbolKey.encode("u-1", "BTC|USDT"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
