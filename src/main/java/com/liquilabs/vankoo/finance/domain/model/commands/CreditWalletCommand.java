package com.liquilabs.vankoo.finance.domain.model.commands;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Credits an already-open wallet. {@code sourceDepositId} is kept for
 * traceability — which deposit funded this movement — not for deduplication:
 * that happens before this command is dispatched (see {@code WalletCreditor}).
 */
public record CreditWalletCommand(
        @TargetAggregateIdentifier WalletId walletId,
        Money amount,
        DepositId sourceDepositId
) {

    public CreditWalletCommand {
        if (walletId == null || amount == null || sourceDepositId == null) {
            throw new IllegalArgumentException("walletId, amount and sourceDepositId are required");
        }
    }
}
