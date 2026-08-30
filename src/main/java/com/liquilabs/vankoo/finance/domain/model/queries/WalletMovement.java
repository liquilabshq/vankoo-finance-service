package com.liquilabs.vankoo.finance.domain.model.queries;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.MovementDirection;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletMovementKind;

import java.time.Instant;

/**
 * One row of a wallet's movement history.
 *
 * @param sourceDepositId the deposit that funded this movement — {@code null}
 *                        for every {@code type} except {@code RECARGA}
 */
public record WalletMovement(
        WalletMovementKind type,
        MovementDirection direction,
        Money amount,
        DepositId sourceDepositId,
        Instant occurredAt) {
}
