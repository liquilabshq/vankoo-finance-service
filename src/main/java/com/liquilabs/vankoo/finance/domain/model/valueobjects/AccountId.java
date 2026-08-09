package com.liquilabs.vankoo.finance.domain.model.valueobjects;

import java.util.UUID;

/**
 * Identity of the investor's wallet receiving the deposit. UUIDv7, like
 * {@link DepositId}, but Finance never generates one: it always arrives from
 * elsewhere in Vankoo.
 */
public record AccountId(UUID value) {

    public AccountId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }

    public static AccountId of(String value) {
        return new AccountId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
