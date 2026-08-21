package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositStatus;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.DepositViewEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Plain Spring Data repository over {@code deposit_views}. No native SQL:
 * unlike the {@code finance_ops} tables, Axon delivers each aggregate's
 * events in order and effectively single-writer within a processing group,
 * so there is no cross-instance race for a derived query to defend against.
 *
 * <p>Keyed by the entity's synthetic {@code UUID id}, not {@code DepositId}
 * (Hibernate does not allow an {@code AttributeConverter} on {@code @Id}) —
 * {@code depositId} is looked up through {@code findByDepositId}/
 * {@code existsByDepositId} instead.
 */
@Repository
public interface DepositViewRepository extends JpaRepository<DepositViewEntity, UUID> {

    Optional<DepositViewEntity> findByDepositId(DepositId depositId);

    boolean existsByDepositId(DepositId depositId);

    Page<DepositViewEntity> findByAccountIdOrderByCreatedAtDesc(AccountId accountId, Pageable pageable);

    Page<DepositViewEntity> findByAccountIdAndStatusOrderByCreatedAtDesc(
            AccountId accountId, DepositStatus status, Pageable pageable);
}
