package com.liquilabs.vankoo.finance.domain.model.valueobjects;

import java.time.Instant;

/**
 * An external event from the provider, already authenticated and normalized.
 *
 * <p>It deliberately carries <strong>no</strong> {@code depositId}. Resolving
 * {@code providerDepositId → depositId} is the webhook inbox's job, against
 * {@code finance_ops.deposit_provider_references}: the provider does not know our
 * identifiers, and a webhook must never be able to invent a deposit.
 *
 * <p>It carries neither the raw payload nor the signature. Both stay in
 * {@code interfaces} and {@code infrastructure}, per the contract rule against
 * letting raw provider payloads into the domain.
 */
public record VerifiedProviderDepositUpdate(
        Provider provider,
        ProviderDepositId providerDepositId,
        ProviderEventId providerEventId,
        NormalizedDepositStatus status,
        Instant observedAt,
        FailureReason failureReason,
        String cancellationReason
) {

    public VerifiedProviderDepositUpdate {
        if (provider == null) {
            throw new IllegalArgumentException("provider is required");
        }
        if (providerDepositId == null || providerEventId == null || status == null || observedAt == null) {
            throw new IllegalArgumentException(
                    "providerDepositId, providerEventId, status and observedAt are required");
        }
    }
}
