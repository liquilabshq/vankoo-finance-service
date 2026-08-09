package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.IdempotencyKey;

/**
 * Input of {@code createDeposit}.
 *
 * <p>Money travels flattened as {@code amountMinor} + {@code currency}, the
 * same representation the contract already fixes for event JSON and for
 * Kafka — no {@code Money} value object here, and {@code depositId} travels
 * as a {@code String}: the port is not domain, and does not construct
 * aggregate identifiers.
 */
public record CreateProviderDepositRequest(
        IdempotencyKey idempotencyKey,
        String depositId,
        long amountMinor,
        String currency,
        String description
) {

    public CreateProviderDepositRequest {
        if (idempotencyKey == null) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        if (depositId == null || depositId.isBlank()) {
            throw new IllegalArgumentException("depositId is required");
        }
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be greater than zero");
        }
        // Only the ISO 4217 shape is checked here. Whether the currency belongs to
        // the supported catalogue is a business invariant, and the aggregate
        // enforces it before the port is ever invoked.
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO 4217 code");
        }
    }
}
