package com.liquilabs.vankoo.finance.domain.model.valueobjects;

/**
 * Whether a wallet movement increased or decreased the balance. Maps 1:1 to
 * the "Efecto" column of {@code wallet-contracts.md}'s movement table:
 * Recarga → CREDIT, Inversión/Retiro/Comisión → DEBIT.
 */
public enum MovementDirection {
    CREDIT,
    DEBIT
}
