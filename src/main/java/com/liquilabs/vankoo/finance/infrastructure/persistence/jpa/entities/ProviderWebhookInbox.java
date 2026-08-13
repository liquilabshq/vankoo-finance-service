package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.FailureReason;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.NormalizedDepositStatus;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderEventId;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.DepositIdConverter;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.ProviderDepositIdConverter;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.ProviderEventIdConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A verified external event, recorded before it becomes a command.
 *
 * <p>Maps {@code finance_ops.provider_webhook_inboxes}. The table name is
 * derived by the project's naming strategy (snake_case + plural), which is why
 * it reads plural here and singular in the contract's prose.
 *
 * <p>It keeps no raw payload: {@code payloadRef} is the SHA-256 of the body,
 * enough to correlate a row with a delivery in the Stripe dashboard, and the
 * normalized fields are everything a retry needs.
 */
@Entity
@Table(schema = "finance_ops")
@Getter
@NoArgsConstructor
public class ProviderWebhookInbox {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Convert(converter = ProviderEventIdConverter.class)
    @Column(nullable = false)
    private ProviderEventId providerEventId;

    @Convert(converter = ProviderDepositIdConverter.class)
    @Column(nullable = false)
    private ProviderDepositId providerDepositId;

    /** Null until the provider reference is resolved — the parked case. */
    @Convert(converter = DepositIdConverter.class)
    private DepositId depositId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WebhookInboxStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NormalizedDepositStatus normalizedStatus;

    @Enumerated(EnumType.STRING)
    private FailureReason failureReason;

    private String cancellationReason;

    @Column(nullable = false)
    private Instant observedAt;

    @Column(nullable = false)
    private String payloadRef;

    @Column(nullable = false)
    private int attempts;

    private Instant nextAttemptAt;

    @Column(length = 1023)
    private String lastError;

    @Column(nullable = false)
    private Instant receivedAt;

    private Instant updatedAt;

    /**
     * Records the outcome of one sweep attempt.
     *
     * <p>{@code depositId} is only ever set, never cleared: once the reference
     * resolves, it stays resolved.
     */
    public void markApplied(DepositId depositId, Instant now) {
        this.depositId = depositId;
        this.status = WebhookInboxStatus.APPLIED;
        this.attempts++;
        this.nextAttemptAt = null;
        this.lastError = null;
        this.updatedAt = now;
    }

    /**
     * Parks the row for another attempt. Used both for the early-arrival case
     * and for transient failures; on exhaustion the caller stops scheduling but
     * the row stays {@code PARKED}, never {@code DISCARDED}.
     */
    public void park(Instant nextAttemptAt, String lastError, Instant now) {
        this.status = WebhookInboxStatus.PARKED;
        this.attempts++;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = truncate(lastError);
        this.updatedAt = now;
    }

    /** Gives up permanently: retrying cannot change the aggregate's answer. */
    public void discard(DepositId depositId, String lastError, Instant now) {
        this.depositId = depositId;
        this.status = WebhookInboxStatus.DISCARDED;
        this.attempts++;
        this.nextAttemptAt = null;
        this.lastError = truncate(lastError);
        this.updatedAt = now;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1023 ? message : message.substring(0, 1023);
    }
}
