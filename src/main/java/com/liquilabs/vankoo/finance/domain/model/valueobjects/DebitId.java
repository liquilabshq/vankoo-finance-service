package com.liquilabs.vankoo.finance.domain.model.valueobjects;

import com.fasterxml.uuid.Generators;

import java.util.UUID;

/**
 * Identity of one debit against a wallet — the reference that makes a debit
 * addressable after the fact: it travels in {@code WalletDebitedEvent}, lands on
 * the {@code wallet_movements} row it produced, and is what the client hands to
 * Investment as its {@code transactionId}. Minted once per request at the REST
 * layer, exactly like {@link DepositId}; an idempotent replay returns the same
 * one instead of minting another.
 *
 * <p>UUIDv7 for the same reason as {@code DepositId}: inserts stay index-local
 * and the value sorts by creation time.
 */
public record DebitId(UUID value) {

    public DebitId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }

    /** Mints a new, time-ordered identity. */
    public DebitId() {
        this(Generators.timeBasedEpochGenerator().generate());
    }

    public static DebitId of(String value) {
        return new DebitId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
