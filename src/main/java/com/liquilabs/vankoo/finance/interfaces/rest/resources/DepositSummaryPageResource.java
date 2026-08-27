package com.liquilabs.vankoo.finance.interfaces.rest.resources;

import java.util.List;

/** Response shape for {@code GET /v1/accounts/{accountId}/deposits}, mirroring {@code DepositSummaryPage}. */
public record DepositSummaryPageResource(
        List<DepositResource> items,
        int pageNumber,
        int pageSize,
        long totalElements) {
}
