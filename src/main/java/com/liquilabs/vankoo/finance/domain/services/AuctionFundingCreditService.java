package com.liquilabs.vankoo.finance.domain.services;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AuctionId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;

/**
 * Pays a MYPE the advance of its invoice once the auction is fully funded.
 *
 * <p>Idempotent by auction: the same auction credits once, however many times
 * the fact reaches Finance.
 */
public interface AuctionFundingCreditService {

    /**
     * @return {@code true} when this call credited the wallet, {@code false}
     *         when the auction had already been credited (nothing done)
     */
    boolean creditAdvance(AuctionId auctionId, AccountId mypeAccountId, Money advance);
}
