package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.WalletDebitCommandIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** JPA access to the wallet debit idempotency barrier. */
@Repository
public interface WalletDebitCommandIdempotencyRepository extends JpaRepository<WalletDebitCommandIdempotency, UUID> {

    Optional<WalletDebitCommandIdempotency> findByAccountIdAndIdempotencyKey(AccountId accountId, String idempotencyKey);

    /**
     * Records that {@code debitId} was the first dispatch for this
     * {@code (accountId, idempotencyKey)}, tolerating a repeat.
     *
     * <p>A single statement on purpose, same reasoning as
     * {@code DepositCommandIdempotencyRepository.insertIfAbsent}: two
     * concurrent requests carrying the same key must not both dispatch
     * {@code DebitWalletCommand} — that is exactly the double debit the
     * barrier exists to prevent. {@code ON CONFLICT DO NOTHING} closes the
     * window in the database.
     *
     * @return {@code 1} when the row was inserted (this call is the one
     *         allowed to dispatch), {@code 0} when this key was already seen
     *         (re-read it — replay or conflict, never dispatch)
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO finance_ops.wallet_debit_command_idempotencies
                (id, account_id, idempotency_key, debit_id, request_hash, created_at)
            VALUES (:id, :accountId, :idempotencyKey, :debitId, :requestHash, :createdAt)
            ON CONFLICT (account_id, idempotency_key) DO NOTHING
            """)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("accountId") UUID accountId,
                       @Param("idempotencyKey") String idempotencyKey,
                       @Param("debitId") UUID debitId,
                       @Param("requestHash") String requestHash,
                       @Param("createdAt") Instant createdAt);
}
