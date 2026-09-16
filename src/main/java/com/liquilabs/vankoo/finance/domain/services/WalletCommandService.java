package com.liquilabs.vankoo.finance.domain.services;

import com.liquilabs.vankoo.finance.domain.model.commands.DebitWalletCommand;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DebitId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.IdempotencyKey;

/**
 * The write use case behind
 * {@code POST /api/v1/accounts/{accountId}/wallets/{currency}/debits}. Same
 * shape as {@link DepositCommandService}: the domain names the use case, the
 * application layer wires it to {@code CommandGateway} and to the idempotency
 * barrier ({@code application.internal.commandservices.WalletCommandServiceImpl}).
 *
 * <p>{@code accountId} and {@code idempotencyKey} travel beside the command
 * rather than inside it: the aggregate needs neither (it is addressed by
 * {@code walletId}, which already encodes the account), but the barrier is
 * keyed on {@code (accountId, idempotencyKey)} exactly like the deposit one.
 * A repeat of the same key with the same content returns the {@code DebitId}
 * it returned the first time without dispatching again; the same key with
 * different content is a conflict.
 */
public interface WalletCommandService {

    DebitId handle(DebitWalletCommand command, AccountId accountId, IdempotencyKey idempotencyKey);
}
