package com.liquilabs.vankoo.finance.domain.model.queries;

import java.util.List;

/**
 * A page of {@link WalletMovement}. Hand-rolled instead of Spring Data's
 * {@code Page}, so that no framework type crosses into {@code domain}.
 */
public record WalletMovementPage(
        List<WalletMovement> items,
        int pageNumber,
        int pageSize,
        long totalElements) {
}
