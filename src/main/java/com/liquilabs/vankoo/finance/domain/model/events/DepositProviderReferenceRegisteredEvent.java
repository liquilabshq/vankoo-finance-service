package com.liquilabs.vankoo.finance.domain.model.events;

/**
 * The provider's own reference was registered against the deposit. Not
 * published to Kafka: it carries a provider reference, which is our own
 * plumbing, not a business fact other bounded contexts need.
 */
public record DepositProviderReferenceRegisteredEvent(
        String depositId,
        String provider,
        String providerDepositId,
        String actionUrl
) {
}
