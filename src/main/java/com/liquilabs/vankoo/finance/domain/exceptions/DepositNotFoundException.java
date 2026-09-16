package com.liquilabs.vankoo.finance.domain.exceptions;

/** {@code GET /api/v1/deposits/{depositId}} addressed a deposit that does not exist. */
public class DepositNotFoundException extends RuntimeException {

    public DepositNotFoundException(String depositId) {
        super("No deposit " + depositId + " exists");
    }
}
