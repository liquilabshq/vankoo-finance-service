package com.liquilabs.vankoo.finance.domain.model.events;

/** A wallet's balance increased. Internal only. */
public record WalletCreditedEvent(
        String walletId,
        long amountMinor,
        String currency,
        String sourceDepositId
) {
}
