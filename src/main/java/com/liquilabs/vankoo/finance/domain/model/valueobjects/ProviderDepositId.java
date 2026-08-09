package com.liquilabs.vankoo.finance.domain.model.valueobjects;

/**
 * Opaque reference the provider assigns to the deposit.
 *
 * <p>Its shape is never parsed nor validated: Stripe promises no particular
 * format, and validating one would break us the day they change it.
 */
public record ProviderDepositId(String value) {

    public ProviderDepositId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("providerDepositId must not be blank");
        }
    }
}
