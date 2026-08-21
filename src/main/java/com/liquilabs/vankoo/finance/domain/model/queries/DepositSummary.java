package com.liquilabs.vankoo.finance.domain.model.queries;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositStatus;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.FailureReason;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositId;

import java.time.Instant;

/**
 * What the business asks about a deposit — the {@code Read Model} row
 * translated into value objects, never the JPA {@code @Entity} itself.
 *
 * <p>Deliberately excludes {@code lastEventId} and {@code projectionVersion}:
 * those are the projection's own bookkeeping, not something a query caller
 * should ever see.
 */
public record DepositSummary(
        DepositId depositId,
        AccountId accountId,
        Money amount,
        Provider provider,
        ProviderDepositId providerDepositId,
        String description,
        DepositStatus status,
        String actionUrl,
        FailureReason failureReason,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt) {
}
