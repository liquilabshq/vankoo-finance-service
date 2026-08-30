package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.MovementDirection;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletMovementKind;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.AccountIdConverter;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.DepositIdConverter;
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
 * Maps {@code finance_read_model.wallet_movements} — one row per wallet
 * event, never updated after insert. The first append-only Read Model table
 * in this service: {@link DepositViewEntity}/{@link WalletViewEntity} are
 * upserts, one row per aggregate updated in place.
 *
 * <p>Rows are written by {@code WalletMovementRepository.insertIfAbsent}
 * (native SQL), not through this entity's persistence lifecycle — it exists
 * so the row can be read back through Spring Data ({@code findByWalletId...}).
 * There is nothing to mutate, so there are no business methods here, unlike
 * {@link WalletViewEntity}.
 *
 * <p>{@code id} is a synthetic UUIDv7, kept as the primary key on purpose —
 * this table grows without bound (a wallet accumulates one row per movement
 * for its whole life), so it is exactly the table where insert locality
 * matters most. {@code eventId} is a separate {@code UNIQUE} column used only
 * to deduplicate the insert against Axon's at-least-once redelivery — Axon's
 * own message identifiers are ordinary random UUIDs, not time-ordered, so
 * they are not used as the primary key.
 */
@Entity
@Table(name = "wallet_movements", schema = "finance_read_model")
@Getter
@NoArgsConstructor
public class WalletMovementEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Convert(converter = WalletIdConverter.class)
    @Column(nullable = false)
    private WalletId walletId;

    @Convert(converter = AccountIdConverter.class)
    @Column(nullable = false)
    private AccountId accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(nullable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletMovementKind type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementDirection direction;

    /** Set only when {@link #type} is {@code RECARGA}. */
    @Convert(converter = DepositIdConverter.class)
    private DepositId sourceDepositId;

    @Column(nullable = false)
    private Instant occurredAt;
}
