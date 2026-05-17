package ch.unisg.cryptoflow.user.application.port.in;

import ch.unisg.cryptoflow.user.domain.User;

public interface UpdateDisplayCurrencyUseCase {

    User updateDisplayCurrency(UpdateDisplayCurrencyCommand command);
}
