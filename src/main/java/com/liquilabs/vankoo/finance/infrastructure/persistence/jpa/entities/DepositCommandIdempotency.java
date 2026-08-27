package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.AccountIdConverter;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.DepositIdConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per {@code (accountId, idempotencyKey)} ever admitted by
 * {@code POST /v1/deposits}. Maps
 * {@code finance_ops.deposit_command_idempotencies} — the barrier
 * {@code DepositCommandServiceImpl} checks before dispatching
 * {@code InitiateDepositCommand}.
 */
@Entity
@Table(schema = "finance_ops")
@Getter
@NoArgsConstructor
public class DepositCommandIdempotency {

    @Id
    private UUID id;

    @Convert(converter = AccountIdConverter.class)
    @Column(nullable = false)
    private AccountId accountId;

    @Column(nullable = false)
    private String idempotencyKey;

    @Convert(converter = DepositIdConverter.class)
    @Column(nullable = false)
    private DepositId depositId;

    @Column(nullable = false)
    private String requestHash;

    @Column(nullable = false)
    private Instant createdAt;
}
