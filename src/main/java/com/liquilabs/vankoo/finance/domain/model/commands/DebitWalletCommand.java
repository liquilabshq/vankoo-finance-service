package com.liquilabs.vankoo.finance.domain.model.commands;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletMovementType;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Debits an already-open wallet, rejected if {@code amount} exceeds the
 * current balance. No caller dispatches this yet — Investment's own contract
 * for requesting a debit is not defined (see {@code wallet-contracts.md}).
 */
public record DebitWalletCommand(
        @TargetAggregateIdentifier WalletId walletId,
        Money amount,
        WalletMovementType reason
) {

    public DebitWalletCommand {
        if (walletId == null || amount == null || reason == null) {
            throw new IllegalArgumentException("walletId, amount and reason are required");
        }
    }
}
