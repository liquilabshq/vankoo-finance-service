package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.FailureReason;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.NormalizedDepositStatus;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositId;

import java.time.Instant;

/**
 * Output of {@code getDeposit}: the status observed at the provider, already
 * normalized to the Finance taxonomy.
 *
 * <p>{@code failureReason} only arrives when {@code status} is
 * {@link NormalizedDepositStatus#FAILED}.
 */
public record ProviderDepositStatus(
        ProviderDepositId providerDepositId,
        NormalizedDepositStatus status,
        Instant observedAt,
        FailureReason failureReason
) {

    public ProviderDepositStatus {
        if (providerDepositId == null || status == null || observedAt == null) {
            throw new IllegalArgumentException("providerDepositId, status and observedAt are required");
        }
    }
}
