package com.liquilabs.vankoo.finance.domain.services;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.VerifiedProviderDepositUpdate;

/**
 * Use case for admitting and applying a provider's webhook.
 *
 * <p>Declared here because the domain states what the use cases are; the
 * application layer wires them to Axon and to the operational tables. The
 * implementation is
 * {@code application.internal.commandservices.WebhookInboxServiceImpl}.
 *
 * <p>The two operations are deliberately separate and never run together. That
 * split is the design: {@link #accept} is everything that happens before the
 * provider is acknowledged, and it is the only thing the HTTP response waits
 * for. Stripe retries a delivery it considers slow, so acknowledging after the
 * command would manufacture the very duplicates the inbox exists to absorb.
 */
public interface WebhookInboxService {

    /** Outcome of admitting an event. A verified event is always recorded, or already was. */
    enum InboxAdmission {
        ACCEPTED,
        DUPLICATE
    }

    /**
     * Records a verified event unless it has already been seen, and returns
     * without resolving or dispatching anything.
     *
     * <p>{@link InboxAdmission#DUPLICATE} means no command will be dispatched,
     * which is what makes a repeated webhook incapable of counting a deposit
     * twice.
     *
     * @param payloadRef digest of the raw payload — the body itself is never
     *                   stored, and never reaches this layer
     */
    InboxAdmission accept(VerifiedProviderDepositUpdate update, String payloadRef);

    /**
     * Resolves the deposit for every event that is due and turns it into a
     * command.
     *
     * <p>An event whose provider reference is not registered yet is parked and
     * retried, never rejected — and it never creates a deposit.
     */
    void resolveAndApply();
}
