package com.liquilabs.vankoo.finance.domain.model.commands;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AuctionId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Credits a MYPE's already-open wallet with the advance of an invoice whose
 * auction was fully funded. The twin of {@link CreditWalletCommand}, with the
 * auction instead of a deposit as the source.
 *
 * <p>{@code sourceAuctionId} is kept for traceability, not for deduplication:
 * that happens before this command is dispatched (see
 * {@code AuctionFundingCreditor}), exactly like the deposit credit.
 */
public record CreditWalletFromAuctionCommand(
        @TargetAggregateIdentifier WalletId walletId,
        Money amount,
        AuctionId sourceAuctionId
) {

    public CreditWalletFromAuctionCommand {
        if (walletId == null || amount == null || sourceAuctionId == null) {
            throw new IllegalArgumentException("walletId, amount and sourceAuctionId are required");
        }
    }
}
