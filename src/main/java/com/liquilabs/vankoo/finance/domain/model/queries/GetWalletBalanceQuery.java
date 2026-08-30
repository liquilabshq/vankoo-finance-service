package com.liquilabs.vankoo.finance.domain.model.queries;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;

/**
 * Asks for the current balance of an investor's wallet in one currency.
 *
 * <p>Carries the raw business key, not an already-derived {@code WalletId}:
 * unlike {@code DepositId}, {@code WalletId} is a pure function of
 * {@code (accountId, currency)} with no I/O — deriving it is the query
 * handler's job, not every caller's.
 */
public record GetWalletBalanceQuery(AccountId accountId, Currency currency) {
}
