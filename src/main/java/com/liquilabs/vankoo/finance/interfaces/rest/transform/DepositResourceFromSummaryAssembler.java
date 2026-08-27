package com.liquilabs.vankoo.finance.interfaces.rest.transform;

import com.liquilabs.vankoo.finance.domain.model.queries.DepositSummary;
import com.liquilabs.vankoo.finance.interfaces.rest.resources.DepositResource;

/** Assemblers are static, like the guide's {@code BookCargoCommandDTOAssembler}. */
public final class DepositResourceFromSummaryAssembler {

    private DepositResourceFromSummaryAssembler() {
    }

    public static DepositResource toResourceFromSummary(DepositSummary summary) {
        return new DepositResource(
                summary.depositId().toString(),
                summary.accountId().toString(),
                summary.amount().amountMinor(),
                summary.amount().currency().name(),
                summary.provider().name(),
                summary.status().name(),
                summary.actionUrl(),
                summary.failureReason() == null ? null : summary.failureReason().name(),
                summary.description(),
                summary.cancellationReason(),
                summary.createdAt().toString(),
                summary.updatedAt().toString());
    }
}
