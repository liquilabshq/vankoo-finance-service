package com.liquilabs.vankoo.finance.domain.model.commands;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.FailureReason;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.NormalizedDepositStatus;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderEventId;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.time.Instant;

/**
 * Applies a status normalized from a webhook or a provider query.
 *
 * <p>{@code failureReason} is only meaningful when {@code status} is
 * {@code FAILED}; {@code cancellationReason} only when it is {@code CANCELLED}.
 * Both are optional.
 */
public record ApplyProviderDepositUpdateCommand(
        @TargetAggregateIdentifier DepositId depositId,
        Provider provider,
        ProviderDepositId providerDepositId,
        ProviderEventId providerEventId,
        NormalizedDepositStatus status,
        Instant observedAt,
        FailureReason failureReason,
        String cancellationReason
) {

    public ApplyProviderDepositUpdateCommand {
        if (depositId == null || provider == null || providerDepositId == null
                || providerEventId == null || status == null || observedAt == null) {
            throw new IllegalArgumentException(
                    "depositId, provider, providerDepositId, providerEventId, status and observedAt are required");
        }
    }
}
