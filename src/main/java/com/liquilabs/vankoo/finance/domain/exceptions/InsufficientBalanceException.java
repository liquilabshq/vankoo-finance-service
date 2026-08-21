package com.liquilabs.vankoo.finance.domain.exceptions;

/** A debit would take the wallet's balance below zero — "no puedes invertir más de lo que tienes". */
public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(String walletId, long balanceMinor, long requestedMinor) {
        super("Wallet " + walletId + " has balance " + balanceMinor
                + " and cannot cover a debit of " + requestedMinor);
    }
}
