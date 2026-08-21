package com.liquilabs.vankoo.finance.domain.model.events;

/** A wallet's balance decreased. Internal only. */
public record WalletDebitedEvent(
        String walletId,
        long amountMinor,
        String currency,
        String reason
) {
}
