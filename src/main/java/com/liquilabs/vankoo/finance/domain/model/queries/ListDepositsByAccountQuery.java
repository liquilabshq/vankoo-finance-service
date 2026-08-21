package com.liquilabs.vankoo.finance.domain.model.queries;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositStatus;

/**
 * Asks for a page of an account's deposit history.
 *
 * @param status     when {@code null}, no status filter is applied — the only
 *                   filter v1 needs
 * @param pageNumber 0-based
 */
public record ListDepositsByAccountQuery(
        AccountId accountId,
        DepositStatus status,
        int pageNumber,
        int pageSize) {
}
