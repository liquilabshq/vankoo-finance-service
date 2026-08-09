package com.liquilabs.vankoo.finance.domain.model.valueobjects;

import com.liquilabs.vankoo.finance.domain.model.exceptions.UnsupportedCurrencyException;

/**
 * Currency catalogue supported by Finance in v1. Modeled as an enum so an
 * unsupported currency cannot be represented at all once inside the domain.
 */
public enum Currency {
    PEN,
    USD;

    /**
     * Parses an ISO 4217 code coming from outside the domain (JSON, HTTP).
     *
     * @throws UnsupportedCurrencyException if {@code isoCode} is not in the catalogue
     */
    public static Currency fromIsoCode(String isoCode) {
        try {
            return Currency.valueOf(isoCode);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new UnsupportedCurrencyException(isoCode);
        }
    }
}
