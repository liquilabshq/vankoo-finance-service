package com.liquilabs.vankoo.finance.interfaces.rest.resources;

/** Response shape for a wallet's balance. */
public record WalletResource(
        String walletId,
        String accountId,
        String currency,
        long balanceMinor,
        String createdAt,
        String updatedAt) {
}
