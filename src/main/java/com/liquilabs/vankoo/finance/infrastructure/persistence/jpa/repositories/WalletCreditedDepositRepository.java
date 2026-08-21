package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories;

import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.WalletCreditedDeposit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/** JPA access to the wallet-crediting deduplication barrier. */
@Repository
public interface WalletCreditedDepositRepository extends JpaRepository<WalletCreditedDeposit, UUID> {

    /**
     * Records that {@code depositId} credited {@code walletId}, tolerating a
     * repeat.
     *
     * <p>A single statement on purpose, same reasoning as
     * {@code ProviderWebhookInboxRepository.insertIfAbsent}: an {@code exists()}
     * followed by an {@code insert} leaves a window where two concurrent
     * redeliveries of the same event both see nothing and both insert.
     * {@code ON CONFLICT DO NOTHING} closes it in the database.
     *
     * @return {@code 1} when the row was inserted (dispatch the command),
     *         {@code 0} when this deposit already credited this wallet
     *         (skip — dispatching again would double-credit)
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO finance_ops.wallet_credited_deposits (id, wallet_id, deposit_id, credited_at)
            VALUES (:id, :walletId, :depositId, :creditedAt)
            ON CONFLICT (wallet_id, deposit_id) DO NOTHING
            """)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("walletId") UUID walletId,
                       @Param("depositId") UUID depositId,
                       @Param("creditedAt") Instant creditedAt);
}
