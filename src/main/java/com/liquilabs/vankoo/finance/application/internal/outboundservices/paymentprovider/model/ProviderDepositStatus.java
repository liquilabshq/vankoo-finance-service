package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model;

import java.time.Instant;

/**
 * Output of {@code PaymentProvider.getDeposit}: the status observed at the
 * provider, already normalized to the Finance taxonomy.
 *
 * <p>{@code failureReason} only arrives when {@code status} is
 * {@link NormalizedDepositStatus#FAILED}, and it is a Finance-normalized reason
 * ({@code DECLINED}, {@code EXPIRED}, {@code INVALID_PAYMENT_METHOD},
 * {@code PROVIDER_ERROR}, {@code UNKNOWN}), never an internal Stripe code.
 */
public record ProviderDepositStatus(
        ProviderDepositId providerDepositId,
        NormalizedDepositStatus status,
        Instant observedAt,
        String failureReason
) {

    public ProviderDepositStatus {
        if (providerDepositId == null || status == null || observedAt == null) {
            throw new IllegalArgumentException("providerDepositId, status and observedAt are required");
        }
    }
}
