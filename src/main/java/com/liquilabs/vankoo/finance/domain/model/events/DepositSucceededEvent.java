package com.liquilabs.vankoo.finance.domain.model.events;

/**
 * The deposit is confirmed. Public — this is the fact the future
 * {@code Wallet} aggregate will consume to credit the investor's balance.
 */
public record DepositSucceededEvent(
        String depositId,
        String accountId,
        long amountMinor,
        String currency,
        String provider
) {
}
