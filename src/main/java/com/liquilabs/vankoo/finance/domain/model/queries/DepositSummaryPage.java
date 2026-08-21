package com.liquilabs.vankoo.finance.domain.model.queries;

import java.util.List;

/**
 * A page of {@link DepositSummary}. Hand-rolled instead of Spring Data's
 * {@code Page}, so that no framework type crosses into {@code domain}.
 */
public record DepositSummaryPage(
        List<DepositSummary> items,
        int pageNumber,
        int pageSize,
        long totalElements) {
}
