package com.liquilabs.vankoo.finance.domain.model.exceptions;

/**
 * The deposit is already in a terminal state ({@code SUCCEEDED}, {@code FAILED}
 * or {@code CANCELLED}) and an update arrived that does not repeat that same
 * outcome (invariant 7). An exact repeat is idempotent and does not throw.
 */
public class TerminalStateTransitionException extends RuntimeException {

    public TerminalStateTransitionException(String message) {
        super(message);
    }
}
