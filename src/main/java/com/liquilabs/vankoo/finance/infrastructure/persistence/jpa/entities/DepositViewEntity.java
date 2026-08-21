package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositStatus;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.FailureReason;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositId;
import com.fasterxml.uuid.Generators;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.AccountIdConverter;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.DepositIdConverter;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.ProviderDepositIdConverter;
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
 * Maps {@code finance_read_model.deposit_views} — the Read Model row.
 *
 * <p>Mutated only through the intent-revealing methods below, one per event
 * type the projection handles, never through setters: a projection is a
 * translation of events into state, not a generic bag of fields.
 *
 * <p>{@code id} is a synthetic technical key, same pattern as the
 * {@code finance_ops} entities — Hibernate rejects an {@code AttributeConverter}
 * directly on {@code @Id} ({@code 'AttributeConverter' not allowed for
 * attribute 'depositId' annotated '@Id'}), so {@link #depositId} stays a
 * regular, unique-constrained column instead of doubling as the primary key.
 */
@Entity
@Table(name = "deposit_views", schema = "finance_read_model")
@Getter
@NoArgsConstructor
public class DepositViewEntity {

    @Id
    private UUID id;

    @Convert(converter = DepositIdConverter.class)
    @Column(nullable = false, unique = true)
    private DepositId depositId;

    @Convert(converter = AccountIdConverter.class)
    @Column(nullable = false)
    private AccountId accountId;

    @Column(nullable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    /** Null until {@code DepositProviderReferenceRegisteredEvent} arrives. */
    @Convert(converter = ProviderDepositIdConverter.class)
    private ProviderDepositId providerDepositId;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepositStatus status;

    private String actionUrl;

    @Enumerated(EnumType.STRING)
    private FailureReason failureReason;

    private String cancellationReason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Id of the last applied {@code EventMessage}, for replay idempotency. */
    @Column(nullable = false)
    private String lastEventId;

    @Column(nullable = false)
    private int projectionVersion;

    /** Only {@code DepositInitiatedEvent} creates a row. */
    public static DepositViewEntity initiate(DepositId depositId, AccountId accountId, long amountMinor,
                                              Currency currency, Provider provider, String description,
                                              String lastEventId, Instant occurredAt) {
        DepositViewEntity entity = new DepositViewEntity();
        entity.id = Generators.timeBasedEpochGenerator().generate();
        entity.depositId = depositId;
        entity.accountId = accountId;
        entity.amountMinor = amountMinor;
        entity.currency = currency;
        entity.provider = provider;
        entity.description = description;
        entity.status = DepositStatus.PENDING;
        entity.createdAt = occurredAt;
        entity.updatedAt = occurredAt;
        entity.lastEventId = lastEventId;
        entity.projectionVersion = 1;
        return entity;
    }

    /**
     * {@code DepositProviderReferenceRegisteredEvent} does not carry
     * {@code status}, amount, account, currency or provider — this method
     * touches only what the event actually reports.
     */
    public void registerProviderReference(ProviderDepositId providerDepositId, String actionUrl,
                                           String lastEventId, Instant occurredAt) {
        this.providerDepositId = providerDepositId;
        this.actionUrl = actionUrl;
        applied(lastEventId, occurredAt);
    }

    public void markActionRequired(String lastEventId, Instant occurredAt) {
        this.status = DepositStatus.ACTION_REQUIRED;
        applied(lastEventId, occurredAt);
    }

    public void markProcessing(String lastEventId, Instant occurredAt) {
        this.status = DepositStatus.PROCESSING;
        applied(lastEventId, occurredAt);
    }

    public void markSucceeded(String lastEventId, Instant occurredAt) {
        this.status = DepositStatus.SUCCEEDED;
        applied(lastEventId, occurredAt);
    }

    public void markFailed(FailureReason failureReason, String lastEventId, Instant occurredAt) {
        this.status = DepositStatus.FAILED;
        this.failureReason = failureReason;
        applied(lastEventId, occurredAt);
    }

    public void markCancelled(String cancellationReason, String lastEventId, Instant occurredAt) {
        this.status = DepositStatus.CANCELLED;
        this.cancellationReason = cancellationReason;
        applied(lastEventId, occurredAt);
    }

    /**
     * Tells the projection whether {@code eventId} was already applied to this
     * row — the guard every mutator except {@link #initiate} requires first,
     * so a redelivered or replayed event does not double-apply.
     */
    public boolean alreadyApplied(String eventId) {
        return eventId.equals(this.lastEventId);
    }

    private void applied(String lastEventId, Instant occurredAt) {
        this.lastEventId = lastEventId;
        this.updatedAt = occurredAt;
        this.projectionVersion++;
    }
}
