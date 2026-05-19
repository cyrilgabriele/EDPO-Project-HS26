package ch.unisg.cryptoflow.user.application.service;

import ch.unisg.cryptoflow.user.application.port.in.UpdateDisplayCurrencyCommand;
import ch.unisg.cryptoflow.user.application.port.in.UpdateDisplayCurrencyUseCase;
import ch.unisg.cryptoflow.user.application.port.out.LoadUserPort;
import ch.unisg.cryptoflow.user.application.port.out.PublishDisplayCurrencyPort;
import ch.unisg.cryptoflow.user.application.port.out.SaveUserPort;
import ch.unisg.cryptoflow.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class UpdateDisplayCurrencyService implements UpdateDisplayCurrencyUseCase {

    /**
     * Initial supported set from ADR-0028. Grows as fx-rate-service adds
     * currency pairs; the validator rejects anything outside this set so the
     * downstream LocalizedPrice consumers can rely on the user's pick being
     * present in the prices map.
     */
    public static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "CHF", "GBP");

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final PublishDisplayCurrencyPort publishDisplayCurrencyPort;

    @Override
    public User updateDisplayCurrency(UpdateDisplayCurrencyCommand command) {
        String normalised = command.displayCurrency().toUpperCase();
        if (!SUPPORTED_CURRENCIES.contains(normalised)) {
            throw new UnsupportedDisplayCurrencyException(normalised, SUPPORTED_CURRENCIES);
        }

        User user = loadUserPort.loadUser(command.userId())
                .orElseThrow(() -> new UserNotFoundException(command.userId()));

        User updated = saveUserPort.save(user.withDisplayCurrency(normalised));
        publishDisplayCurrencyPort.publish(updated.userId(), updated.displayCurrency());
        return updated;
    }

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String userId) {
            super("User not found: " + userId);
        }
    }

    public static class UnsupportedDisplayCurrencyException extends RuntimeException {
        public UnsupportedDisplayCurrencyException(String code, Set<String> supported) {
            super("Display currency '" + code + "' is not supported. Supported: " + supported);
        }
    }
}
