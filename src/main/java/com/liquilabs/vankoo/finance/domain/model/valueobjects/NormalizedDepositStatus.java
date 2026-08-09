package com.liquilabs.vankoo.finance.domain.model.valueobjects;

/**
 * The Finance status taxonomy that adapters translate provider observations
 * into, before any command is issued.
 *
 * <p>It does not include {@code PENDING}: that is the initial status the
 * aggregate gives itself when it accepts the deposit, and it never comes
 * from an external observation.
 */
public enum NormalizedDepositStatus {
    ACTION_REQUIRED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
