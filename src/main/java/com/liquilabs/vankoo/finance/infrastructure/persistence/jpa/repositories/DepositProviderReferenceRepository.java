package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositId;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.DepositProviderReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** JPA access to the provider reference table the inbox resolves against. */
@Repository
public interface DepositProviderReferenceRepository extends JpaRepository<DepositProviderReference, UUID> {

    Optional<DepositProviderReference> findByProviderAndProviderDepositId(
            Provider provider, ProviderDepositId providerDepositId);

    /**
     * Registers the translation, tolerating a repeat.
     *
     * <p>Axon may replay {@code DepositProviderReferenceRegisteredEvent} — after
     * a token reset, or simply because delivery is at-least-once — so the write
     * has to be idempotent. The aggregate already guarantees a deposit never
     * registers two different references, which is why a conflict here can be
     * ignored rather than reconciled.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO finance_ops.deposit_provider_references (
                id, provider, provider_deposit_id, deposit_id, registered_at)
            VALUES (:id, :provider, :providerDepositId, :depositId, :registeredAt)
            ON CONFLICT (provider, provider_deposit_id) DO NOTHING
            """)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("provider") String provider,
                       @Param("providerDepositId") String providerDepositId,
                       @Param("depositId") UUID depositId,
                       @Param("registeredAt") Instant registeredAt);
}
