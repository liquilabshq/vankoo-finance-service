package com.liquilabs.vankoo.finance.domain.model.commands;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Opens a wallet with balance zero. {@code walletId} must already be
 * {@link WalletId#derive(AccountId, Currency)} of {@code accountId} and
 * {@code currency} — this command does not derive it itself.
 */
public record OpenWalletCommand(
        @TargetAggregateIdentifier WalletId walletId,
        AccountId accountId,
        Currency currency
) {

    public OpenWalletCommand {
        if (walletId == null || accountId == null || currency == null) {
            throw new IllegalArgumentException("walletId, accountId and currency are required");
        }
    }
}
