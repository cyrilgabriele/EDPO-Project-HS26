package ch.unisg.cryptoflow.userservice.adapter.out.persistence;

import ch.unisg.cryptoflow.userservice.application.port.out.SaveUserPort;
import ch.unisg.cryptoflow.userservice.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements SaveUserPort {

    private final SpringDataUserRepository userRepository;

    @Override
    public User save(User user) {
        UserEntity entity = UserEntity.fromDomain(user);
        UserEntity persisted = userRepository.save(entity);
        return persisted.toDomain();
    }
}
