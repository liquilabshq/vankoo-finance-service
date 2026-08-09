package com.liquilabs.vankoo.finance.domain.model.events;

/** The provider requires an action from the investor before it can proceed. */
public record DepositActionRequiredEvent(
        String depositId,
        String accountId,
        long amountMinor,
        String currency,
        String provider
) {
}
