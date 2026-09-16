package com.liquilabs.vankoo.finance.domain.model.events;

/**
 * A wallet's balance decreased. Internal only.
 *
 * <p>{@code debitId} was added after this event was already being persisted in
 * Axon Server, so it is deliberately the last component and nullable: events
 * stored before it existed deserialize with {@code null} there (Jackson leaves a
 * missing record component null), and nothing was renamed. Every event applied
 * since carries the {@code DebitId} of the command that produced it.
 */
public record WalletDebitedEvent(
        String walletId,
        long amountMinor,
        String currency,
        String reason,
        String debitId
) {
}
