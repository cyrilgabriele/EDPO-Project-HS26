package ch.unisg.cryptoflow.userservice.application.port.in;

import ch.unisg.cryptoflow.userservice.domain.User;

public interface CreateUserUseCase {
    User createUser(CreateUserCommand command);
}
