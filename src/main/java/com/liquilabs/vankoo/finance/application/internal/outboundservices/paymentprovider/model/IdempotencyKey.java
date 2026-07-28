package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model;

/**
 * Idempotency key supplied by the client. Opaque: no format is imposed.
 *
 * <p>PROVISIONAL: pending review with Salim, same case as
 * {@link ProviderDepositId}.
 */
public record IdempotencyKey(String value) {

    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }
}
