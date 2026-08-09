package com.liquilabs.vankoo.finance.domain.model.valueobjects;

import com.fasterxml.uuid.Generators;

import java.util.UUID;

/**
 * Identity of a deposit. UUIDv7 so inserts stay index-local in
 * {@code deposit_view} and {@code finance_ops}, and are sortable by creation
 * time without a separate {@code created_at} lookup.
 */
public record DepositId(UUID value) {

    public DepositId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }

    /**
     * Genera un DepositId nuevo con UUIDv7, ordenable por tiempo de creación.
     */
    public DepositId() {
        this(Generators.timeBasedEpochGenerator().generate());
    }

    public static DepositId of(String value) {
        return new DepositId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
