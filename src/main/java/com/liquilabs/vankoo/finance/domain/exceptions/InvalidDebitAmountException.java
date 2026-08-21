package com.liquilabs.vankoo.finance.domain.exceptions;

/** A {@code DebitWalletCommand}'s amount was not strictly positive. */
public class InvalidDebitAmountException extends RuntimeException {

    public InvalidDebitAmountException(long amountMinor) {
        super("Wallet debit amount must be greater than zero, was: " + amountMinor);
    }
}
