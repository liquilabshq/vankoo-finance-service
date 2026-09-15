package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.DepositCommandIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** JPA access to the {@code POST /api/v1/deposits} idempotency barrier. */
@Repository
public interface DepositCommandIdempotencyRepository extends JpaRepository<DepositCommandIdempotency, UUID> {

    Optional<DepositCommandIdempotency> findByAccountIdAndIdempotencyKey(AccountId accountId, String idempotencyKey);

    /**
     * Records that {@code depositId} was the first dispatch for this
     * {@code (accountId, idempotencyKey)}, tolerating a repeat.
     *
     * <p>A single statement on purpose, same reasoning as
     * {@code WalletCreditedDepositRepository.insertIfAbsent}: an
     * {@code exists()} followed by an {@code insert} leaves a window where two
     * concurrent requests carrying the same key both see nothing and both
     * dispatch {@code InitiateDepositCommand} — creating two aggregates for
     * one logical request. {@code ON CONFLICT DO NOTHING} closes it in the
     * database.
     *
     * @return {@code 1} when the row was inserted (this call is the one
     *         allowed to dispatch), {@code 0} when this key was already seen
     *         (re-read it — replay or conflict, never dispatch)
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO finance_ops.deposit_command_idempotencies
                (id, account_id, idempotency_key, deposit_id, request_hash, created_at)
            VALUES (:id, :accountId, :idempotencyKey, :depositId, :requestHash, :createdAt)
            ON CONFLICT (account_id, idempotency_key) DO NOTHING
            """)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("accountId") UUID accountId,
                       @Param("idempotencyKey") String idempotencyKey,
                       @Param("depositId") UUID depositId,
                       @Param("requestHash") String requestHash,
                       @Param("createdAt") Instant createdAt);
}
