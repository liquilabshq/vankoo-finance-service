package com.liquilabs.vankoo.finance.domain.model.valueobjects;

import java.time.Instant;

/**
 * The status observed at the provider, already normalized to the Finance
 * taxonomy. It is the answer to asking, which is the reconciliation path when a
 * webhook is lost.
 *
 * <p>{@code failureReason} only arrives when {@code status} is
 * {@link NormalizedDepositStatus#FAILED}, and it is a Finance-normalized
 * {@link FailureReason}, never an internal provider code.
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
