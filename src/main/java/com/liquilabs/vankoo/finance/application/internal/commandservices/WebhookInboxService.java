package com.liquilabs.vankoo.finance.application.internal.commandservices;

import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.VerifiedProviderDepositUpdate;
import com.liquilabs.vankoo.finance.domain.exceptions.ProviderReferenceMismatchException;
import com.liquilabs.vankoo.finance.domain.exceptions.TerminalStateTransitionException;
import com.liquilabs.vankoo.finance.domain.model.commands.ApplyProviderDepositUpdateCommand;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.infrastructure.configuration.WebhookInboxProperties;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.DepositProviderReference;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.ProviderWebhookInbox;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories.DepositProviderReferenceRepository;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories.ProviderWebhookInboxRepository;
import com.fasterxml.uuid.Generators;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The webhook inbox: one component for the three problems an external event
 * brings — duplicates, arrival order and retries.
 *
 * <p>It is split in two halves that never run together:
 *
 * <ul>
 *   <li>{@link #accept} is synchronous, and it is the <em>only</em> thing that
 *       happens before the provider is acknowledged. It records the event and
 *       returns. It does not resolve, does not dispatch and does not wait.</li>
 *   <li>{@link #resolveAndApply} runs on a sweep, resolves the deposit and
 *       turns the row into a command.</li>
 * </ul>
 *
 * <p>The split is the point. Stripe retries a delivery it considers slow, so
 * acknowledging only after the command would manufacture the very duplicates
 * the inbox exists to absorb.
 */
@Service
public class WebhookInboxService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookInboxService.class);

    /** Outcome of admitting an event. There is no rejection: a verified event is always recorded, or already was. */
    public enum InboxAdmission {
        ACCEPTED,
        DUPLICATE
    }

    private final ProviderWebhookInboxRepository inboxRepository;
    private final DepositProviderReferenceRepository referenceRepository;
    private final CommandGateway commandGateway;
    private final WebhookInboxProperties properties;

    public WebhookInboxService(ProviderWebhookInboxRepository inboxRepository,
                               DepositProviderReferenceRepository referenceRepository,
                               CommandGateway commandGateway,
                               WebhookInboxProperties properties) {
        this.inboxRepository = inboxRepository;
        this.referenceRepository = referenceRepository;
        this.commandGateway = commandGateway;
        this.properties = properties;
    }

    /**
     * Records a verified event, unless it has already been seen.
     *
     * <p>Deduplication is the database's job: a single conflict-tolerant insert
     * against {@code UNIQUE(provider, provider_event_id)}. When it reports a
     * duplicate, no command is dispatched, and that is what makes a repeated
     * webhook incapable of counting a deposit twice.
     *
     * @param payloadRef digest of the raw payload — the body itself is never
     *                   stored, and never reaches this layer
     */
    @Transactional
    public InboxAdmission accept(VerifiedProviderDepositUpdate update, String payloadRef) {
        int inserted = inboxRepository.insertIfAbsent(
                Generators.timeBasedEpochGenerator().generate(),
                update.provider().name(),
                update.providerEventId().value(),
                update.providerDepositId().value(),
                update.status().name(),
                update.failureReason() == null ? null : update.failureReason().name(),
                update.cancellationReason(),
                update.observedAt(),
                payloadRef,
                Instant.now());

        if (inserted == 0) {
            LOGGER.info("Webhook already seen, ignoring: provider={}, providerEventId={}",
                    update.provider(), update.providerEventId().value());
            return InboxAdmission.DUPLICATE;
        }
        return InboxAdmission.ACCEPTED;
    }

    /**
     * Resolves the deposit for every due row and applies it.
     *
     * <p>Runs in one transaction that holds the claimed rows locked, so the
     * batch is kept small. A crash between the command and the commit leaves the
     * row due again: the command is then re-sent, which is harmless because the
     * aggregate's terminal states make a repeat produce no event.
     */
    @Scheduled(fixedDelayString = "${vankoo.finance.webhook-inbox.poll-interval:2s}")
    @Transactional
    public void resolveAndApply() {
        Instant now = Instant.now();
        List<ProviderWebhookInbox> due =
                inboxRepository.claimDue(now, properties.getMaxAttempts(), properties.getBatchSize());

        for (ProviderWebhookInbox row : due) {
            applyOne(row, now);
        }
    }

    private void applyOne(ProviderWebhookInbox row, Instant now) {
        Optional<DepositProviderReference> reference = referenceRepository
                .findByProviderAndProviderDepositId(row.getProvider(), row.getProviderDepositId());

        if (reference.isEmpty()) {
            // The webhook overtook our own RegisterDepositProviderReferenceCommand.
            // It is parked and retried, never rejected — and it never creates a
            // deposit, because a provider update is not evidence that a deposit
            // exists on our side.
            park(row, "provider reference not registered yet", now);
            return;
        }

        DepositId depositId = reference.get().getDepositId();
        var command = new ApplyProviderDepositUpdateCommand(
                depositId,
                row.getProvider(),
                row.getProviderDepositId(),
                row.getProviderEventId(),
                row.getNormalizedStatus(),
                row.getObservedAt(),
                row.getFailureReason(),
                row.getCancellationReason());

        try {
            // TODO (Tarjeta 4): dispatch through domain.services.DepositCommandService
            // once it exists. The class diagram already shows that delegation; the
            // interface is the aggregate owner's to declare, so until it lands the
            // gateway is used directly. Only this call changes.
            commandGateway.sendAndWait(command);
            row.markApplied(depositId, now);
        } catch (RuntimeException exception) {
            if (isPermanent(exception)) {
                LOGGER.error("Webhook rejected by the deposit for good: providerEventId={}, depositId={}",
                        row.getProviderEventId().value(), depositId, exception);
                row.discard(depositId, exception.getMessage(), now);
            } else {
                park(row, exception.getMessage(), now);
            }
        }
    }

    private void park(ProviderWebhookInbox row, String reason, Instant now) {
        Duration delay = backoffFor(row.getAttempts());
        row.park(now.plus(delay), reason, now);

        if (row.getAttempts() >= properties.getMaxAttempts()) {
            // Deliberately an error: the row stops being retried and nothing else
            // will move it. It stays PARKED so a human can see it.
            LOGGER.error("Webhook exhausted its attempts and stays PARKED: provider={}, providerEventId={}, "
                            + "providerDepositId={}, lastError={}",
                    row.getProvider(), row.getProviderEventId().value(),
                    row.getProviderDepositId().value(), reason);
        } else {
            LOGGER.warn("Webhook parked, attempt {} of {}: providerEventId={}, reason={}",
                    row.getAttempts(), properties.getMaxAttempts(), row.getProviderEventId().value(), reason);
        }
    }

    /** Exponential, capped. {@code attempts} is the count before this failure. */
    private Duration backoffFor(int attempts) {
        Duration base = properties.getBackoffBase();
        Duration max = properties.getBackoffMax();
        // Shift instead of pow, and stop early: 2^attempts overflows long past 62.
        if (attempts >= 32) {
            return max;
        }
        Duration delay = base.multipliedBy(1L << attempts);
        return delay.compareTo(max) > 0 ? max : delay;
    }

    /**
     * Tells a definitive refusal from a transient one.
     *
     * <p>Only the aggregate's own rules are permanent: a terminal deposit or a
     * reference that does not match will answer the same way forever. Anything
     * else — Axon Server unreachable, the aggregate not visible yet — is worth
     * another attempt.
     *
     * <p>Commands travel through Axon Server, which may deliver the failure
     * wrapped rather than as the original type. When the cause chain does not
     * identify it, the row is parked rather than discarded: retrying a hopeless
     * row costs a few attempts and then reaches a human, whereas discarding a
     * retryable one silently loses a deposit update.
     */
    private static boolean isPermanent(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof TerminalStateTransitionException
                    || cause instanceof ProviderReferenceMismatchException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
