package com.liquilabs.vankoo.finance.interfaces.rest.transform;

import com.liquilabs.vankoo.finance.domain.model.commands.InitiateDepositCommand;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositStatus;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.IdempotencyKey;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import com.liquilabs.vankoo.finance.interfaces.rest.resources.CreateDepositResource;
import com.liquilabs.vankoo.finance.interfaces.rest.resources.DepositResource;

import java.time.Instant;

/**
 * Assemblers are static, like the guide's {@code BookCargoCommandDTOAssembler}.
 * The {@code Idempotency-Key} arrives as an HTTP header, not in the body, so
 * {@code DepositController} passes it in explicitly.
 */
public final class CreateDepositCommandFromResourceAssembler {

    private CreateDepositCommandFromResourceAssembler() {
    }

    public static InitiateDepositCommand toCommandFromResource(CreateDepositResource resource,
                                                                 String idempotencyKey) {
        return new InitiateDepositCommand(
                new DepositId(),
                AccountId.of(resource.accountId()),
                new Money(resource.amountMinor(), Currency.fromIsoCode(resource.currency())),
                Provider.valueOf(resource.provider()),
                new IdempotencyKey(idempotencyKey),
                Instant.now(),
                resource.description());
    }

    /**
     * Builds the {@code 202} body for a <em>first-time</em> creation, without
     * querying the Read Model. {@code DepositProjection}'s
     * {@code @EventHandler} runs on Axon's own event processor, not inside
     * {@code sendAndWait} — querying immediately after a successful dispatch
     * risks reading before the row is projected. Every field here is either
     * an input we already have, or {@code PENDING}, which is guaranteed if
     * {@code sendAndWait} did not throw.
     */
    public static DepositResource toPendingResourceFrom(DepositId depositId, CreateDepositResource resource) {
        String now = Instant.now().toString();
        return new DepositResource(
                depositId.toString(),
                resource.accountId(),
                resource.amountMinor(),
                resource.currency(),
                resource.provider(),
                DepositStatus.PENDING.name(),
                null,
                null,
                resource.description(),
                null,
                now,
                now);
    }
}
