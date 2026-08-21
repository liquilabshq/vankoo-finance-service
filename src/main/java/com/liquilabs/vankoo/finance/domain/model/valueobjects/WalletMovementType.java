package com.liquilabs.vankoo.finance.domain.model.valueobjects;

/**
 * Reasons a {@code Wallet} can be debited, besides a failed/insufficient
 * check. {@code RECARGA} is not here: crediting from a deposit is its own
 * command, not a debit reason. {@code REVERSO} is not here either: its shape
 * is not designed yet (see {@code wallet-contracts.md}).
 */
public enum WalletMovementType {
    INVERSION,
    RETIRO,
    COMISION
}
