package com.liquilabs.vankoo.finance.interfaces.rest.transform;

import com.liquilabs.vankoo.finance.domain.model.commands.DebitWalletCommand;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DebitId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletMovementType;
import com.liquilabs.vankoo.finance.interfaces.rest.resources.CreateWalletDebitResource;
import com.liquilabs.vankoo.finance.interfaces.rest.resources.WalletDebitResource;

/**
 * Assemblers are static, like the guide's {@code BookCargoCommandDTOAssembler}.
 * The account and currency come from the path, so {@code WalletController}
 * passes them in already parsed; the {@code DebitId} is minted here, once per
 * HTTP request, the same way {@code CreateDepositCommandFromResourceAssembler}
 * mints the {@code DepositId}.
 */
public final class DebitWalletCommandFromResourceAssembler {

    private DebitWalletCommandFromResourceAssembler() {
    }

    /**
     * @throws IllegalArgumentException when {@code reason} is not a
     *                                  {@code WalletMovementType} — mapped to
     *                                  {@code 400 invalid-request}
     */
    public static DebitWalletCommand toCommandFromResource(AccountId accountId,
                                                           Currency currency,
                                                           CreateWalletDebitResource resource) {
        return new DebitWalletCommand(
                WalletId.derive(accountId, currency),
                new DebitId(),
                new Money(resource.amountMinor(), currency),
                WalletMovementType.valueOf(resource.reason()));
    }

    /**
     * Builds the {@code 201} body from the command itself, never from the
     * Read Model: the projection runs on Axon's event processor, not inside
     * {@code sendAndWait}, so querying right after a successful dispatch could
     * read before the movement row exists. Every field here is an input the
     * command already carried — a replay of the same {@code Idempotency-Key}
     * substitutes the original {@code debitId} and nothing else differs.
     */
    public static WalletDebitResource toResourceFrom(DebitId debitId, AccountId accountId, DebitWalletCommand command) {
        return new WalletDebitResource(
                debitId.toString(),
                command.walletId().toString(),
                accountId.toString(),
                command.amount().currency().name(),
                command.amount().amountMinor(),
                command.reason().name());
    }
}
