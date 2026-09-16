package com.liquilabs.vankoo.finance.interfaces.rest.resources;

/**
 * Response shape for one row of a wallet's movement history.
 *
 * @param sourceDepositId set only for {@code RECARGA}
 * @param debitId         set only for debits requested through
 *                        {@code POST .../debits} — the same value that
 *                        request was answered with
 */
public record WalletMovementResource(
        String type,
        String direction,
        long amountMinor,
        String currency,
        String sourceDepositId,
        String debitId,
        String occurredAt) {
}
