package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model;

/**
 * Input of {@code PaymentProvider.createDeposit}.
 *
 * <p>Money travels flattened as {@code amountMinor} + {@code currency}, the same
 * representation the contract already fixes for event JSON and for Kafka. No
 * {@code Money} value object is used because it does not exist in
 * {@code domain} yet.
 *
 * <p>{@code depositId} travels as a {@code String} for the same reason: the
 * {@code DepositId} value object belongs to the domain and arrives with the
 * aggregate.
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
