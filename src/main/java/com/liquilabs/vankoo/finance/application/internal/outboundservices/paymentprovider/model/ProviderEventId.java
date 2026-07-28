package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model;

/**
 * Identifier of the provider's external event. It is the deduplication key of
 * the webhook inbox: {@code UNIQUE(provider, provider_event_id)}.
 *
 * <p>Opaque, just like {@link ProviderDepositId}.
 *
 * <p>PROVISIONAL: pending review with Salim, same case as
 * {@link ProviderDepositId}.
 */
public record ProviderEventId(String value) {

    public ProviderEventId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("providerEventId must not be blank");
        }
    }
}
