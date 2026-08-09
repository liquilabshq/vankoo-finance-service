package com.liquilabs.vankoo.finance.domain.model.valueobjects;

/**
 * The {@code Deposit} aggregate's own lifecycle status. Unlike
 * {@link NormalizedDepositStatus}, it includes {@code PENDING} — the state the
 * aggregate gives itself on creation, before any external observation.
 *
 * <p>{@code SUCCEEDED}, {@code FAILED} and {@code CANCELLED} are terminal for v1.
 */
public enum DepositStatus {
    PENDING,
    ACTION_REQUIRED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
