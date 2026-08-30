package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.fasterxml.uuid.Generators;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.AccountIdConverter;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.WalletIdConverter;
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
 * Maps {@code finance_read_model.wallet_views} — the running-balance Read
 * Model row, one per wallet.
 *
 * <p>Named explicitly (not left to the naming strategy to derive from the
 * class name): {@code WalletViewEntity} would otherwise pluralize to
 * {@code wallet_view_entities}, same reasoning as {@link DepositViewEntity}.
 *
 * <p>Mutated only through {@link #open}/{@link #credit}/{@link #debit}, never
 * through setters — a projection is a translation of events into state.
 *
 * <p>{@code id} is a synthetic technical key: Hibernate rejects an
 * {@code AttributeConverter} directly on {@code @Id}, so {@link #walletId}
 * stays a regular, unique-constrained column instead of doubling as the
 * primary key.
 */
@Entity
@Table(name = "wallet_views", schema = "finance_read_model")
@Getter
@NoArgsConstructor
public class WalletViewEntity {

    @Id
    private UUID id;

    @Convert(converter = WalletIdConverter.class)
    @Column(nullable = false, unique = true)
    private WalletId walletId;

    @Convert(converter = AccountIdConverter.class)
    @Column(nullable = false)
    private AccountId accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(nullable = false)
    private long balanceMinor;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Id of the last applied {@code EventMessage}, for replay idempotency. */
    @Column(nullable = false)
    private String lastEventId;

    @Column(nullable = false)
    private int projectionVersion;

    /** Only {@code WalletOpenedEvent} creates a row, always with balance zero. */
    public static WalletViewEntity open(WalletId walletId, AccountId accountId, Currency currency,
                                         String lastEventId, Instant occurredAt) {
        WalletViewEntity entity = new WalletViewEntity();
        entity.id = Generators.timeBasedEpochGenerator().generate();
        entity.walletId = walletId;
        entity.accountId = accountId;
        entity.currency = currency;
        entity.balanceMinor = 0L;
        entity.createdAt = occurredAt;
        entity.updatedAt = occurredAt;
        entity.lastEventId = lastEventId;
        entity.projectionVersion = 1;
        return entity;
    }

    public void credit(long amountMinor, String lastEventId, Instant occurredAt) {
        this.balanceMinor += amountMinor;
        applied(lastEventId, occurredAt);
    }

    public void debit(long amountMinor, String lastEventId, Instant occurredAt) {
        this.balanceMinor -= amountMinor;
        applied(lastEventId, occurredAt);
    }

    /**
     * Tells the projection whether {@code eventId} was already applied to this
     * row — the guard {@link #credit}/{@link #debit} require first, so a
     * redelivered or replayed event does not double-apply.
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
