package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories;

import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.ProviderWebhookInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA access to the webhook inbox. The two statements that carry the design are
 * native on purpose — neither has a portable JPQL equivalent.
 */
@Repository
public interface ProviderWebhookInboxRepository extends JpaRepository<ProviderWebhookInbox, UUID> {

    /**
     * Records a verified event unless its {@code (provider, providerEventId)} is
     * already known.
     *
     * <p>This is the deduplication barrier, and it is a <strong>single</strong>
     * statement on purpose: an {@code exists()} followed by an {@code insert}
     * leaves a window in which two concurrent deliveries of the same event both
     * see nothing and both insert. {@code ON CONFLICT DO NOTHING} closes it in
     * the database, where the unique index already is.
     *
     * @return {@code 1} when the row was inserted, {@code 0} when it is a
     *         duplicate and no command must be dispatched
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO finance_ops.provider_webhook_inboxes (
                id, provider, provider_event_id, provider_deposit_id, deposit_id,
                status, normalized_status, failure_reason, cancellation_reason,
                observed_at, payload_ref, attempts, next_attempt_at, last_error,
                received_at, updated_at)
            VALUES (
                :id, :provider, :providerEventId, :providerDepositId, NULL,
                'RECEIVED', :normalizedStatus, :failureReason, :cancellationReason,
                :observedAt, :payloadRef, 0, NULL, NULL,
                :receivedAt, NULL)
            ON CONFLICT (provider, provider_event_id) DO NOTHING
            """)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("provider") String provider,
                       @Param("providerEventId") String providerEventId,
                       @Param("providerDepositId") String providerDepositId,
                       @Param("normalizedStatus") String normalizedStatus,
                       @Param("failureReason") String failureReason,
                       @Param("cancellationReason") String cancellationReason,
                       @Param("observedAt") Instant observedAt,
                       @Param("payloadRef") String payloadRef,
                       @Param("receivedAt") Instant receivedAt);

    /**
     * Takes the next rows that are due, locking them so a second instance of the
     * service picks different ones.
     *
     * <p>{@code SKIP LOCKED} is what makes the sweep safe to run on more than
     * one node without a leader election: a row already being worked on is
     * stepped over instead of waited for.
     *
     * <p>The {@code attempts} filter is how a row leaves the retry loop without
     * leaving {@code PARKED}: once it has exhausted its attempts it simply stops
     * being due, and stays in the table for a human to decide. It is never
     * discarded — a webhook we could not resolve is not a webhook we may drop.
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM finance_ops.provider_webhook_inboxes
            WHERE status IN ('RECEIVED', 'PARKED')
              AND attempts < :maxAttempts
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
            ORDER BY received_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<ProviderWebhookInbox> claimDue(@Param("now") Instant now,
                                        @Param("maxAttempts") int maxAttempts,
                                        @Param("batchSize") int batchSize);
}
