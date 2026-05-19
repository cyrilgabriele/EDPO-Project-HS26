package ch.unisg.cryptoflow.user.application.port.in;

import java.util.Objects;

public record UpdateDisplayCurrencyCommand(String userId, String displayCurrency) {

    public UpdateDisplayCurrencyCommand {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(displayCurrency, "displayCurrency must not be null");
    }
}
