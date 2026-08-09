package com.liquilabs.vankoo.finance.domain.model.valueobjects;

/**
 * Identifier of the provider's external event. It is the deduplication key
 * of the webhook inbox: {@code UNIQUE(provider, provider_event_id)}.
 *
 * <p>Opaque, just like {@link ProviderDepositId}.
 */
public record ProviderEventId(String value) {

    public ProviderEventId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("providerEventId must not be blank");
        }
    }
}
