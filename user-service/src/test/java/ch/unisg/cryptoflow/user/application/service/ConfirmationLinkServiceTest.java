package ch.unisg.cryptoflow.user.application.service;

import ch.unisg.cryptoflow.user.adapter.out.persistence.SpringDataUserConfirmationLinkRepository;
import ch.unisg.cryptoflow.user.adapter.out.persistence.UserConfirmationLinkEntity;
import ch.unisg.cryptoflow.user.domain.ConfirmationLinkStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmationLinkServiceTest {

    @Mock
    private SpringDataUserConfirmationLinkRepository repository;

    private ConfirmationLinkService confirmationLinkService;

    @BeforeEach
    void setUp() {
        confirmationLinkService = new ConfirmationLinkService(repository);
    }

    @Test
    void registersPendingLink() {
        ArgumentCaptor<UserConfirmationLinkEntity> captor = ArgumentCaptor.forClass(UserConfirmationLinkEntity.class);

        confirmationLinkService.registerPendingLink("user-123");

        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ConfirmationLinkStatus.PENDING);
    }

    @Test
    void confirmsPendingLink() {
        UserConfirmationLinkEntity entity = UserConfirmationLinkEntity.pending("user-1");
        when(repository.findById("user-1")).thenReturn(Optional.of(entity));

        boolean result = confirmationLinkService.confirmLink("user-1");

        assertThat(result).isTrue();
        assertThat(entity.getStatus()).isEqualTo(ConfirmationLinkStatus.CONFIRMED);
    }

    @Test
    void invalidatesPendingLink() {
        UserConfirmationLinkEntity entity = UserConfirmationLinkEntity.pending("user-2");
        when(repository.findById("user-2")).thenReturn(Optional.of(entity));

        boolean firstAttempt = confirmationLinkService.invalidateLink("user-2");
        boolean secondAttempt = confirmationLinkService.invalidateLink("user-2");

        assertThat(firstAttempt).isTrue();
        assertThat(secondAttempt).isFalse();
        assertThat(entity.getStatus()).isEqualTo(ConfirmationLinkStatus.INVALIDATED);
    }

    @Test
    void revertConfirmationReturnsPending() {
        UserConfirmationLinkEntity entity = UserConfirmationLinkEntity.pending("user-3");
        entity.markConfirmed();
        when(repository.findById("user-3")).thenReturn(Optional.of(entity));

        confirmationLinkService.revertConfirmation("user-3");

        assertThat(entity.getStatus()).isEqualTo(ConfirmationLinkStatus.PENDING);
    }
}
