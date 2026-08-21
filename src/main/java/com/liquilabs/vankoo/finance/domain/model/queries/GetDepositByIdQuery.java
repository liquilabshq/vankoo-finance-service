package com.liquilabs.vankoo.finance.domain.model.queries;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;

/**
 * Asks for the current projected state of one deposit.
 */
public record GetDepositByIdQuery(DepositId depositId) {
}
