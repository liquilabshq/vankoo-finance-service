package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities;

/**
 * Lifecycle of a row in the webhook inbox. Operational state of the edge, not
 * domain: it says what we have done with an external event, never what happened
 * to the deposit.
 */
public enum WebhookInboxStatus {

    /** Verified and stored. Waiting for the sweep to resolve and apply it. */
    RECEIVED,

    /** The command reached the aggregate. Nothing else to do. */
    APPLIED,

    /**
     * Could not be applied yet — typically the provider reference is not
     * registered. It is retried with backoff, and on exhaustion it stays here
     * for a human to look at. Never rejected, and it never creates a deposit.
     */
    PARKED,

    /**
     * Definitively not applicable: the aggregate refused it for a reason that
     * retrying cannot change. Kept for audit rather than deleted.
     */
    DISCARDED
}
