package com.liquilabs.vankoo.finance.interfaces.rest.resources;

/**
 * Response of a successful debit. {@code debitId} is the movement's
 * reference: it is what the client hands to Investment as
 * {@code transactionId}, and what {@code WalletMovementResource.debitId}
 * shows once the movement is projected.
 */
public record WalletDebitResource(
        String debitId,
        String walletId,
        String accountId,
        String currency,
        long amountMinor,
        String reason) {
}
