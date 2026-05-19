package ch.unisg.cryptoflow.user.application.service;

import ch.unisg.cryptoflow.user.application.UserCreationResult;
import ch.unisg.cryptoflow.user.application.port.in.CreateUserCommand;
import ch.unisg.cryptoflow.user.application.port.in.CreateUserUseCase;
import ch.unisg.cryptoflow.user.application.port.out.LoadUserPort;
import ch.unisg.cryptoflow.user.application.port.out.PublishDisplayCurrencyPort;
import ch.unisg.cryptoflow.user.application.port.out.SaveUserPort;
import ch.unisg.cryptoflow.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class UserCreationService implements CreateUserUseCase {

    private final SaveUserPort saveUserPort;
    private final LoadUserPort loadUserPort;
    private final PublishDisplayCurrencyPort publishDisplayCurrencyPort;

    @Override
    public UserCreationResult createUser(CreateUserCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        return loadUserPort.loadUser(command.userId())
            .map(existing -> new UserCreationResult(existing, false))
            .orElseGet(() -> {
                User user = new User(
                        command.userName(),
                        command.password(),
                        command.userId(),
                        command.email(),
                        User.DEFAULT_DISPLAY_CURRENCY);
                User persisted = saveUserPort.save(user);
                // Emit the initial Display Currency event so portfolio-service and
                // transaction-service can materialise the user in their KTables (ADR-0028).
                publishDisplayCurrencyPort.publish(persisted.userId(), persisted.displayCurrency());
                return new UserCreationResult(persisted, true);
            });
    }
}
