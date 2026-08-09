package com.liquilabs.vankoo.finance.domain.model.events;

/** The deposit was rejected or failed. Public. */
public record DepositFailedEvent(
        String depositId,
        String accountId,
        long amountMinor,
        String currency,
        String provider,
        String failureReason
) {
}
