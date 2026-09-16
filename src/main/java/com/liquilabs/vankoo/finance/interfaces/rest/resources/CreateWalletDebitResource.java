package com.liquilabs.vankoo.finance.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Body of {@code POST /api/v1/accounts/{accountId}/wallets/{currency}/debits}.
 * The account and the currency come from the path, and the
 * {@code Idempotency-Key} from its header — none of them is repeated here, so
 * there is nothing for the body to disagree with.
 *
 * @param reason a {@code WalletMovementType} name; the mobile app sends
 *               {@code INVERSION}
 */
public record CreateWalletDebitResource(
        @Positive long amountMinor,
        @NotBlank String reason) {
}
