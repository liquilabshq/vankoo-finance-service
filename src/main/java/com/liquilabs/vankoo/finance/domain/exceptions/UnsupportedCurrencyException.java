package com.liquilabs.vankoo.finance.domain.exceptions;

/** The currency code does not belong to Finance's supported catalogue (PEN, USD). */
public class UnsupportedCurrencyException extends RuntimeException {

    public UnsupportedCurrencyException(String isoCode) {
        super("Unsupported currency: " + isoCode);
    }
}
