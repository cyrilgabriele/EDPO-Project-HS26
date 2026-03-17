package ch.unisg.cryptoflow.userservice.application.port.out;

import ch.unisg.cryptoflow.userservice.domain.User;

public interface SaveUserPort {
    User save(User user);
}
