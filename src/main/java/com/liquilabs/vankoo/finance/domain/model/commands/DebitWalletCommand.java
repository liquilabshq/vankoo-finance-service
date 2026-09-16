package com.liquilabs.vankoo.finance.domain.model.commands;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.DebitId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletMovementType;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Debits an already-open wallet, rejected if {@code amount} exceeds the
 * current balance.
 *
 * <p>Dispatched by {@code WalletCommandServiceImpl} on behalf of
 * {@code POST /api/v1/accounts/{accountId}/wallets/{currency}/debits}: the
 * investor's own client asks Finance to debit before it asks Investment to
 * invest, and hands Investment the {@code debitId} as its {@code transactionId}.
 * Investment never sends this command itself (see {@code wallet-contracts.md}).
 *
 * <p>{@code debitId} is minted by the caller, not here, so that an idempotent
 * replay of the same request can answer with the id it produced the first
 * time. The aggregate only records it on the event.
 */
public record DebitWalletCommand(
        @TargetAggregateIdentifier WalletId walletId,
        DebitId debitId,
        Money amount,
        WalletMovementType reason
) {

    public DebitWalletCommand {
        if (walletId == null || debitId == null || amount == null || reason == null) {
            throw new IllegalArgumentException("walletId, debitId, amount and reason are required");
        }
    }
}
