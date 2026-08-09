package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositId;

/** Input of {@code getDeposit}: identifies the deposit on the provider's side. */
public record ProviderDepositReference(String provider, ProviderDepositId providerDepositId) {

    public ProviderDepositReference {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }
        if (providerDepositId == null) {
            throw new IllegalArgumentException("providerDepositId is required");
        }
    }
}
