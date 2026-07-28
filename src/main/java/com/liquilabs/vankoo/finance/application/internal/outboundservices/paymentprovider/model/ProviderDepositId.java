package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model;

/**
 * Opaque reference the provider assigns to the deposit.
 *
 * <p>Its shape is never parsed nor validated: Stripe promises no particular
 * format, and validating one would break us the day they change it.
 *
 * <p>PROVISIONAL: pending review with Salim. It may have to move to
 * {@code domain/model/valueobjects} once the {@code Deposit} aggregate exists,
 * since {@code RegisterDepositProviderReferenceCommand} and
 * {@code ApplyProviderDepositUpdateCommand} reference it too.
 */
public record ProviderDepositId(String value) {

    public ProviderDepositId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("providerDepositId must not be blank");
        }
    }
}
