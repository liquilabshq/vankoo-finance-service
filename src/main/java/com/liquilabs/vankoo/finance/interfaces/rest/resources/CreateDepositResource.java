package com.liquilabs.vankoo.finance.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/deposits}. The {@code Idempotency-Key} travels as an
 * HTTP header, not here — see
 * {@code CreateDepositCommandFromResourceAssembler}.
 *
 * <p>{@code description}'s 500-character limit mirrors the existing
 * {@code deposit_views.description} column width (see {@code V3}): it turns
 * what would otherwise be a database error into a 400 at the boundary. It is
 * not an answer to the contract's still-open question of a general size limit
 * for descriptions and external identifiers.
 */
public record CreateDepositResource(
        @NotBlank String accountId,
        @Positive long amountMinor,
        @NotBlank String currency,
        @NotBlank String provider,
        @Size(max = 500) String description) {
}
