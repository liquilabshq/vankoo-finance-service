package com.liquilabs.vankoo.finance.domain.model.events;

/**
 * A wallet was opened with balance zero. Internal only — Wallet has no Kafka
 * publisher in this card.
 */
public record WalletOpenedEvent(
        String walletId,
        String accountId,
        String currency
) {
}
