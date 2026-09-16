package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.WalletMovementEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/** JPA access to {@code wallet_movements}, the append-only movement history. */
@Repository
public interface WalletMovementRepository extends JpaRepository<WalletMovementEntity, UUID> {

    Page<WalletMovementEntity> findByWalletIdOrderByOccurredAtDesc(WalletId walletId, Pageable pageable);

    /**
     * Records one wallet movement, tolerating a redelivery of the same event.
     *
     * <p>A single statement on purpose, same reasoning as
     * {@code WalletCreditedDepositRepository.insertIfAbsent}: there is no
     * existing row to check "already applied" against for a fresh insert (that
     * guard only works for the balance row, which is loaded and mutated in
     * place) — {@code ON CONFLICT (event_id) DO NOTHING} closes the race in
     * the database instead.
     *
     * @return {@code 1} when the row was inserted, {@code 0} when this event
     *         already produced a movement row (redelivery — nothing to do)
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO finance_read_model.wallet_movements
                (id, event_id, wallet_id, account_id, currency, amount_minor,
                 type, direction, source_deposit_id, debit_id, occurred_at)
            VALUES (:id, :eventId, :walletId, :accountId, :currency, :amountMinor,
                    :type, :direction, :sourceDepositId, :debitId, :occurredAt)
            ON CONFLICT (event_id) DO NOTHING
            """)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("eventId") String eventId,
                       @Param("walletId") UUID walletId,
                       @Param("accountId") UUID accountId,
                       @Param("currency") String currency,
                       @Param("amountMinor") long amountMinor,
                       @Param("type") String type,
                       @Param("direction") String direction,
                       @Param("sourceDepositId") UUID sourceDepositId,
                       @Param("debitId") UUID debitId,
                       @Param("occurredAt") Instant occurredAt);
}
