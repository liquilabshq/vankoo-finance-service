package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model;

/**
 * Input of {@code PaymentProvider.getDeposit}: identifies the deposit on the
 * provider's side.
 */
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
