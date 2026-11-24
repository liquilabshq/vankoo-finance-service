package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories;

import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.WalletCreditedAuction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/** JPA access to the auction-advance deduplication barrier. */
@Repository
public interface WalletCreditedAuctionRepository extends JpaRepository<WalletCreditedAuction, UUID> {

    /**
     * Records that {@code auctionId}'s advance credited {@code walletId},
     * tolerating a repeat. Same single-statement reasoning as
     * {@code WalletCreditedDepositRepository.insertIfAbsent}.
     *
     * @return {@code 1} when the row was inserted (dispatch the command),
     *         {@code 0} when this auction was already credited (skip)
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO finance_ops.wallet_credited_auctions (id, wallet_id, auction_id, credited_at)
            VALUES (:id, :walletId, :auctionId, :creditedAt)
            ON CONFLICT (auction_id) DO NOTHING
            """)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("walletId") UUID walletId,
                       @Param("auctionId") UUID auctionId,
                       @Param("creditedAt") Instant creditedAt);
}
