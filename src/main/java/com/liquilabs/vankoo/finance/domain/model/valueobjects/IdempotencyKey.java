package com.liquilabs.vankoo.finance.domain.model.valueobjects;

/** Idempotency key supplied by the client. Opaque: no format is imposed. */
public record IdempotencyKey(String value) {

    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }
}
