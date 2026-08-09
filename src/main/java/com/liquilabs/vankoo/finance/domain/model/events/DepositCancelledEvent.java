package com.liquilabs.vankoo.finance.domain.model.events;

/** The deposit was cancelled or expired. Public. */
public record DepositCancelledEvent(
        String depositId,
        String accountId,
        long amountMinor,
        String currency,
        String provider,
        String cancellationReason
) {
}
