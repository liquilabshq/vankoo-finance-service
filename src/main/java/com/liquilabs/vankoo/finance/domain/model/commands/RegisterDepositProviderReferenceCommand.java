package com.liquilabs.vankoo.finance.domain.model.commands;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositId;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.time.Instant;

/**
 * Associates the provider's own reference with the deposit. Does not change
 * the deposit's status: it happens in parallel to the lifecycle, not inside
 * it. {@code actionUrl} is optional.
 */
public record RegisterDepositProviderReferenceCommand(
        @TargetAggregateIdentifier DepositId depositId,
        Provider provider,
        ProviderDepositId providerDepositId,
        Instant registeredAt,
        String actionUrl
) {

    public RegisterDepositProviderReferenceCommand {
        if (depositId == null || provider == null || providerDepositId == null || registeredAt == null) {
            throw new IllegalArgumentException(
                    "depositId, provider, providerDepositId and registeredAt are required");
        }
    }
}
