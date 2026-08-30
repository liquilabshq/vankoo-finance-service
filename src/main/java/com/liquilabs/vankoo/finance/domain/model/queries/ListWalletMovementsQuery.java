package com.liquilabs.vankoo.finance.domain.model.queries;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;

/**
 * Asks for a page of an investor's wallet movement history, in one currency.
 *
 * @param pageNumber 0-based
 */
public record ListWalletMovementsQuery(
        AccountId accountId,
        Currency currency,
        int pageNumber,
        int pageSize) {
}
