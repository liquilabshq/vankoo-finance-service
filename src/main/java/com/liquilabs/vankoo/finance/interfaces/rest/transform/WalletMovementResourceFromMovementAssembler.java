package com.liquilabs.vankoo.finance.interfaces.rest.transform;

import com.liquilabs.vankoo.finance.domain.model.queries.WalletMovement;
import com.liquilabs.vankoo.finance.interfaces.rest.resources.WalletMovementResource;

/** Assemblers are static, like the guide's {@code BookCargoCommandDTOAssembler}. */
public final class WalletMovementResourceFromMovementAssembler {

    private WalletMovementResourceFromMovementAssembler() {
    }

    public static WalletMovementResource toResourceFromMovement(WalletMovement movement) {
        return new WalletMovementResource(
                movement.type().name(),
                movement.direction().name(),
                movement.amount().amountMinor(),
                movement.amount().currency().name(),
                movement.sourceDepositId() == null ? null : movement.sourceDepositId().toString(),
                movement.debitId() == null ? null : movement.debitId().toString(),
                movement.occurredAt().toString());
    }
}
