package com.liquilabs.vankoo.finance.domain.model.valueobjects;

/**
 * What the provider answered when the charge resource was created.
 *
 * <p>{@code actionUrl} is optional: it only arrives when the provider requires
 * the investor to complete an action.
 */
public record ProviderDepositCreated(
        ProviderDepositId providerDepositId,
        String actionUrl,
        NormalizedDepositStatus status
) {

    public ProviderDepositCreated {
        if (providerDepositId == null) {
            throw new IllegalArgumentException("providerDepositId is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
    }
}
