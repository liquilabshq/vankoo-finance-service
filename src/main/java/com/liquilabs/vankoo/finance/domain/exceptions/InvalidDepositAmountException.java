package com.liquilabs.vankoo.finance.domain.exceptions;

/** The deposit's amount was not strictly positive (invariant 2). */
public class InvalidDepositAmountException extends RuntimeException {

    public InvalidDepositAmountException(long amountMinor) {
        super("Deposit amount must be greater than zero, was: " + amountMinor);
    }
}
