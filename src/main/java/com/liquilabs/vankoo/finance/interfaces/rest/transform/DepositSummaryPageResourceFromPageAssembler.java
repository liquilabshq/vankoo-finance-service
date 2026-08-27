package com.liquilabs.vankoo.finance.interfaces.rest.transform;

import com.liquilabs.vankoo.finance.domain.model.queries.DepositSummaryPage;
import com.liquilabs.vankoo.finance.interfaces.rest.resources.DepositSummaryPageResource;

/** Assemblers are static, like the guide's {@code BookCargoCommandDTOAssembler}. */
public final class DepositSummaryPageResourceFromPageAssembler {

    private DepositSummaryPageResourceFromPageAssembler() {
    }

    public static DepositSummaryPageResource toResourceFromPage(DepositSummaryPage page) {
        return new DepositSummaryPageResource(
                page.items().stream().map(DepositResourceFromSummaryAssembler::toResourceFromSummary).toList(),
                page.pageNumber(),
                page.pageSize(),
                page.totalElements());
    }
}
