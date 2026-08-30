package com.liquilabs.vankoo.finance.interfaces.rest.resources;

/** Response shape for one row of a wallet's movement history. */
public record WalletMovementResource(
        String type,
        String direction,
        long amountMinor,
        String currency,
        String sourceDepositId,
        String occurredAt) {
}
