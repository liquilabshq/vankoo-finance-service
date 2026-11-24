package com.liquilabs.vankoo.finance.domain.model.events;

/**
 * A MYPE's wallet received the advance of a funded auction. Internal only.
 *
 * <p>A new event rather than a {@link WalletCreditedEvent} with an optional
 * auction field: that one says "a deposit credited this wallet" and every
 * stored instance carries a {@code sourceDepositId}. Reusing it would make
 * the field nullable and its meaning depend on which other field is set.
 */
public record WalletAuctionAdvanceCreditedEvent(
        String walletId,
        long amountMinor,
        String currency,
        String sourceAuctionId
) {
}
