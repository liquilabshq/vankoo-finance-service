package com.liquilabs.vankoo.finance.domain.model.valueobjects;

/**
 * Full movement vocabulary for the Read Model, unlike {@link WalletMovementType}
 * which is only the write side's *debit reasons* (deliberately without
 * {@code RECARGA} — crediting from a deposit is its own command, not a debit
 * reason, so {@code WalletCreditedEvent} carries no type of its own). This
 * enum adds {@code RECARGA} as the label a credit is given when projected
 * into wallet history.
 *
 * <p>The three debit names match {@link WalletMovementType}'s literally, so
 * {@code WalletMovementKind.valueOf(event.reason())} works directly with no
 * translation table.
 */
public enum WalletMovementKind {
    RECARGA,
    INVERSION,
    RETIRO,
    COMISION
}
