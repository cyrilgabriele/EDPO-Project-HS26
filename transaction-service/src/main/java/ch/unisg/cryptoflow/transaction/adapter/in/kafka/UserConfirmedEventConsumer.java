package ch.unisg.cryptoflow.transaction.adapter.in.kafka;

import ch.unisg.cryptoflow.events.UserConfirmedEvent;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.ConfirmedUserEntity;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.ConfirmedUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Maintains the local replicated read-model of confirmed users (ADR-0017).
 *
 * <p>Consumes {@link UserConfirmedEvent} from the compacted {@code user.confirmed} topic.
 * Uses {@code saveAndFlush} with upsert semantics via the primary key — re-delivery of
 * the same event overwrites the row harmlessly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserConfirmedEventConsumer {

    private final ConfirmedUserRepository confirmedUserRepository;

    @KafkaListener(
            topics = "${crypto.kafka.topic.user-confirmed}",
            containerFactory = "userConfirmedKafkaListenerContainerFactory"
    )
    public void onUserConfirmed(UserConfirmedEvent event) {
        if (event == null || event.userId() == null) {
            log.warn("Received invalid UserConfirmedEvent: {}", event);
            return;
        }
        log.info("Upserting confirmed user userId={} userName={}", event.userId(), event.userName());
        confirmedUserRepository.save(
                new ConfirmedUserEntity(event.userId(), event.userName(), event.confirmedAt()));
    }
}
