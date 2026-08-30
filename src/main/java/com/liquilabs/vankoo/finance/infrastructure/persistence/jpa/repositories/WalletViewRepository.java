package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.WalletViewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Plain Spring Data repository over {@code wallet_views}. No native SQL:
 * same reasoning as {@code DepositViewRepository} — Axon delivers one
 * wallet's events in order and effectively single-writer within a
 * processing group, so there is no cross-instance race for a derived query
 * to defend against.
 */
@Repository
public interface WalletViewRepository extends JpaRepository<WalletViewEntity, UUID> {

    Optional<WalletViewEntity> findByWalletId(WalletId walletId);

    boolean existsByWalletId(WalletId walletId);
}
