package com.liquilabs.vankoo.finance.domain.model.events;

/**
 * A deposit was accepted for creation. Internal only — not published to Kafka.
 *
 * <p>Fields carry plain types, not value objects, for serialization stability
 * across the Event Store's lifetime.
 *
 * <p>{@code idempotencyKey} and {@code description} are recorded here and
 * only here: they trace back to the client's original request but play no
 * role in the aggregate's own decisions.
 */
public record DepositInitiatedEvent(
        String depositId,
        String accountId,
        long amountMinor,
        String currency,
        String provider,
        String idempotencyKey,
        String description
) {
}
