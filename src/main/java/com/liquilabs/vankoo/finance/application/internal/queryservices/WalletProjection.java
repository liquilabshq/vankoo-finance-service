package com.liquilabs.vankoo.finance.application.internal.queryservices;

import com.liquilabs.vankoo.finance.domain.model.events.WalletCreditedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.WalletDebitedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.WalletOpenedEvent;
import com.liquilabs.vankoo.finance.domain.model.queries.GetWalletBalanceQuery;
import com.liquilabs.vankoo.finance.domain.model.queries.ListWalletMovementsQuery;
import com.liquilabs.vankoo.finance.domain.model.queries.WalletBalance;
import com.liquilabs.vankoo.finance.domain.model.queries.WalletMovement;
import com.liquilabs.vankoo.finance.domain.model.queries.WalletMovementPage;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DebitId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.MovementDirection;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletMovementKind;
import com.liquilabs.vankoo.finance.domain.services.WalletQueryService;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.WalletMovementEntity;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.WalletViewEntity;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories.WalletMovementRepository;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories.WalletViewRepository;
import com.fasterxml.uuid.Generators;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.Timestamp;
import org.axonframework.messaging.annotation.MessageIdentifier;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * The Query Model for {@code Wallet}: projection and query handlers together,
 * same shape as {@link DepositProjection}, feeding two Read Model tables from
 * the same three events instead of one:
 *
 * <ul>
 *   <li>{@code wallet_views} — the running balance, one row per wallet,
 *       updated in place. Guarded by {@code alreadyApplied} exactly like
 *       {@code deposit_views}.</li>
 *   <li>{@code wallet_movements} — append-only history, one new row per
 *       event, never updated. There is no existing row to check
 *       "already applied" against, so its dedup barrier is
 *       {@code insertIfAbsent} (the same idiom {@code finance_ops} tables
 *       use), keyed on Axon's event identifier.</li>
 * </ul>
 *
 * <p>{@code @ProcessingGroup} is declared explicitly, distinct from both
 * {@code deposit-read-model} and {@code WalletCreditor}'s
 * {@code wallet-crediting} (which listens to {@code DepositSucceededEvent},
 * not any {@code Wallet} event — no overlap).
 */
@Component
@ProcessingGroup("wallet-read-model")
public class WalletProjection implements WalletQueryService {

    private final WalletViewRepository walletViewRepository;
    private final WalletMovementRepository walletMovementRepository;

    public WalletProjection(WalletViewRepository walletViewRepository,
                             WalletMovementRepository walletMovementRepository) {
        this.walletViewRepository = walletViewRepository;
        this.walletMovementRepository = walletMovementRepository;
    }

    /** The only event that creates a balance row. No movement row: opening isn't a movement. */
    @EventHandler
    public void on(WalletOpenedEvent event, @MessageIdentifier String eventId, @Timestamp Instant occurredAt) {
        WalletId walletId = WalletId.of(event.walletId());
        if (walletViewRepository.existsByWalletId(walletId)) {
            return;
        }
        walletViewRepository.save(WalletViewEntity.open(
                walletId,
                AccountId.of(event.accountId()),
                Currency.valueOf(event.currency()),
                eventId,
                occurredAt));
    }

    /**
     * {@code WalletCreditedEvent} carries no {@code accountId} — it is read
     * off the balance row, already loaded to apply the credit.
     */
    @EventHandler
    public void on(WalletCreditedEvent event, @MessageIdentifier String eventId, @Timestamp Instant occurredAt) {
        WalletViewEntity row = loadRow(event.walletId(), eventId);
        if (row.alreadyApplied(eventId)) {
            return;
        }
        row.credit(event.amountMinor(), eventId, occurredAt);
        walletViewRepository.save(row);

        walletMovementRepository.insertIfAbsent(
                Generators.timeBasedEpochGenerator().generate(),
                eventId,
                row.getWalletId().value(),
                row.getAccountId().value(),
                row.getCurrency().name(),
                event.amountMinor(),
                WalletMovementKind.RECARGA.name(),
                MovementDirection.CREDIT.name(),
                DepositId.of(event.sourceDepositId()).value(),
                null,
                occurredAt);
    }

    /**
     * {@code debitId} is nullable on the event on purpose — debits stored
     * before the field existed carry none — so it is projected as-is.
     */
    @EventHandler
    public void on(WalletDebitedEvent event, @MessageIdentifier String eventId, @Timestamp Instant occurredAt) {
        WalletViewEntity row = loadRow(event.walletId(), eventId);
        if (row.alreadyApplied(eventId)) {
            return;
        }
        row.debit(event.amountMinor(), eventId, occurredAt);
        walletViewRepository.save(row);

        walletMovementRepository.insertIfAbsent(
                Generators.timeBasedEpochGenerator().generate(),
                eventId,
                row.getWalletId().value(),
                row.getAccountId().value(),
                row.getCurrency().name(),
                event.amountMinor(),
                WalletMovementKind.valueOf(event.reason()).name(),
                MovementDirection.DEBIT.name(),
                null,
                event.debitId() == null ? null : DebitId.of(event.debitId()).value(),
                occurredAt);
    }

    /**
     * {@code WalletOpenedEvent} always precedes a credit/debit for a given
     * wallet, so a missing row here is a projection bug, not a normal branch
     * to swallow — same reasoning as {@code DepositProjection.apply}.
     */
    private WalletViewEntity loadRow(String rawWalletId, String eventId) {
        return walletViewRepository.findByWalletId(WalletId.of(rawWalletId))
                .orElseThrow(() -> new IllegalStateException(
                        "No wallet_views row for walletId=" + rawWalletId + " when applying event " + eventId));
    }

    @Override
    @QueryHandler
    public Optional<WalletBalance> getWalletBalance(GetWalletBalanceQuery query) {
        WalletId walletId = WalletId.derive(query.accountId(), query.currency());
        return walletViewRepository.findByWalletId(walletId).map(this::toBalance);
    }

    @Override
    @QueryHandler
    public WalletMovementPage listWalletMovements(ListWalletMovementsQuery query) {
        WalletId walletId = WalletId.derive(query.accountId(), query.currency());
        Pageable pageable = PageRequest.of(query.pageNumber(), query.pageSize());
        Page<WalletMovementEntity> page = walletMovementRepository.findByWalletIdOrderByOccurredAtDesc(walletId, pageable);

        return new WalletMovementPage(
                page.getContent().stream().map(this::toMovement).toList(),
                query.pageNumber(),
                query.pageSize(),
                page.getTotalElements());
    }

    private WalletBalance toBalance(WalletViewEntity entity) {
        return new WalletBalance(
                entity.getWalletId(),
                entity.getAccountId(),
                new Money(entity.getBalanceMinor(), entity.getCurrency()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private WalletMovement toMovement(WalletMovementEntity entity) {
        return new WalletMovement(
                entity.getType(),
                entity.getDirection(),
                new Money(entity.getAmountMinor(), entity.getCurrency()),
                entity.getSourceDepositId(),
                entity.getDebitId(),
                entity.getOccurredAt());
    }
}
