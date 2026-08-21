package com.liquilabs.vankoo.finance.domain.exceptions;

/** A {@code CreditWalletCommand}'s amount was not strictly positive. */
public class InvalidCreditAmountException extends RuntimeException {

    public InvalidCreditAmountException(long amountMinor) {
        super("Wallet credit amount must be greater than zero, was: " + amountMinor);
    }
}
