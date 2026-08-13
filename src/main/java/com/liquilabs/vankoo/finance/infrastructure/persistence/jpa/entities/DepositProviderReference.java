package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositId;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.DepositIdConverter;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.ProviderDepositIdConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Translation from the provider's own reference to our {@code depositId}.
 *
 * <p>Maps {@code finance_ops.deposit_provider_references}. It is the only thing
 * that lets the inbox tell which deposit a webhook belongs to: the provider does
 * not know our identifiers, and the aggregate is never searched by provider
 * reference.
 */
@Entity
@Table(schema = "finance_ops")
@Getter
@NoArgsConstructor
public class DepositProviderReference {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Convert(converter = ProviderDepositIdConverter.class)
    @Column(nullable = false)
    private ProviderDepositId providerDepositId;

    @Convert(converter = DepositIdConverter.class)
    @Column(nullable = false)
    private DepositId depositId;

    @Column(nullable = false)
    private Instant registeredAt;
}
