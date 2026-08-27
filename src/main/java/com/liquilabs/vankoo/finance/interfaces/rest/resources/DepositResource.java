package com.liquilabs.vankoo.finance.interfaces.rest.resources;

/**
 * Response shape for a deposit. Extends
 * {@code docs/uml/finance-rest-api-class-diagram.puml}'s drawn fields with
 * {@code description}, {@code cancellationReason} and {@code updatedAt}:
 * {@code DepositSummary} already carries all three, and without them a client
 * can never see the description it submitted, or why its deposit was
 * cancelled.
 */
public record DepositResource(
        String depositId,
        String accountId,
        long amountMinor,
        String currency,
        String provider,
        String status,
        String actionUrl,
        String failureReason,
        String description,
        String cancellationReason,
        String createdAt,
        String updatedAt) {
}
