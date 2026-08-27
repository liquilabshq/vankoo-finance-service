package com.liquilabs.vankoo.finance.domain.services;

import com.liquilabs.vankoo.finance.domain.model.commands.InitiateDepositCommand;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;

/**
 * The write use case behind {@code POST /v1/deposits}. Declared here because
 * the domain states what the use cases are; the application layer wires it to
 * {@code CommandGateway} and to the idempotency barrier. The implementation is
 * {@code application.internal.commandservices.DepositCommandServiceImpl}.
 *
 * <p>{@code IdempotencyKey} is mandatory on {@code command}: the same
 * {@code (accountId, idempotencyKey)} seen again with the same content returns
 * the {@code DepositId} it returned the first time, without dispatching the
 * command again — the caller cannot tell a replay from a first call just by
 * looking at the return value, which is why {@code DepositController} compares
 * the returned id against the one it minted.
 */
public interface DepositCommandService {

    DepositId handle(InitiateDepositCommand command);
}
