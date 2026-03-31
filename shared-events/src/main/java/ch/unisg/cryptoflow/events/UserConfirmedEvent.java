package ch.unisg.cryptoflow.events;

import java.time.Instant;

/**
 * Published by {@code user-service} to topic {@code user.confirmed}
 * when a user completes email confirmation during onboarding.
 *
 * <p>Consumed by {@code transaction-service} to maintain a local replicated
 * read-model of confirmed users, enabling synchronisation-free user validation
 * at order placement time (ADR-0017).
 *
 * <p>The topic is log-compacted keyed by {@code userId}, so consumers can always
 * reconstruct the full set of confirmed users by reading from the beginning.
 */
public record UserConfirmedEvent(
        String userId,
        String userName,
        Instant confirmedAt
) {}
