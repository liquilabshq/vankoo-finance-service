package com.liquilabs.vankoo.finance.domain.model.events;

/** The provider reported the deposit entered processing. */
public record DepositProcessingStartedEvent(
        String depositId,
        String accountId,
        long amountMinor,
        String currency,
        String provider
) {
}
