package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model;

import java.time.Instant;

/**
 * Output of {@code PaymentProvider.verifyWebhook}: the external event, already
 * authenticated and normalized.
 *
 * <p>It deliberately carries <strong>no</strong> {@code depositId}. Resolving
 * {@code providerDepositId → depositId} is the webhook inbox's job, against
 * {@code finance_ops.deposit_provider_reference}, not the port's: the provider
 * does not know our identifiers.
 *
 * <p>It carries neither the raw payload nor the signature. Both stay in
 * {@code infrastructure}, per the contract rule against storing raw Stripe
 * payloads inside domain events.
 */
public record VerifiedProviderDepositUpdate(
        String provider,
        ProviderDepositId providerDepositId,
        ProviderEventId providerEventId,
        NormalizedDepositStatus status,
        Instant observedAt,
        String failureReason,
        String cancellationReason
) {

    public VerifiedProviderDepositUpdate {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }
        if (providerDepositId == null || providerEventId == null || status == null || observedAt == null) {
            throw new IllegalArgumentException(
                    "providerDepositId, providerEventId, status and observedAt are required");
        }
    }
}
