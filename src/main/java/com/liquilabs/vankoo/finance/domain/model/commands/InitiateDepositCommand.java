package com.liquilabs.vankoo.finance.domain.model.commands;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.IdempotencyKey;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.time.Instant;

/**
 * Creates a deposit in {@code PENDING} status. {@code description} is optional.
 */
public record InitiateDepositCommand(
        @TargetAggregateIdentifier DepositId depositId,
        AccountId accountId,
        Money amount,
        Provider provider,
        IdempotencyKey idempotencyKey,
        Instant requestedAt,
        String description
) {

    public InitiateDepositCommand {
        if (depositId == null || accountId == null || amount == null || provider == null
                || idempotencyKey == null || requestedAt == null) {
            throw new IllegalArgumentException(
                    "depositId, accountId, amount, provider, idempotencyKey and requestedAt are required");
        }
    }
}
