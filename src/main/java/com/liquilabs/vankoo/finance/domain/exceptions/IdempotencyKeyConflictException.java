package com.liquilabs.vankoo.finance.domain.exceptions;

/**
 * The same {@code (accountId, idempotencyKey)} was seen before with different
 * request content. An exact repeat is idempotent and does not throw — this is
 * only for a reused key whose content changed underneath it.
 */
public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(String message) {
        super(message);
    }
}
