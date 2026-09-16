package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DebitId;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.AccountIdConverter;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.DebitIdConverter;
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
 * {@code POST /api/v1/accounts/{accountId}/wallets/{currency}/debits}. Maps
 * {@code finance_ops.wallet_debit_command_idempotencies} — the barrier
 * {@code WalletCommandServiceImpl} checks before dispatching
 * {@code DebitWalletCommand}, the twin of {@link DepositCommandIdempotency}.
 */
@Entity
@Table(schema = "finance_ops")
@Getter
@NoArgsConstructor
public class WalletDebitCommandIdempotency {

    @Id
    private UUID id;

    @Convert(converter = AccountIdConverter.class)
    @Column(nullable = false)
    private AccountId accountId;

    @Column(nullable = false)
    private String idempotencyKey;

    @Convert(converter = DebitIdConverter.class)
    @Column(nullable = false)
    private DebitId debitId;

    @Column(nullable = false)
    private String requestHash;

    @Column(nullable = false)
    private Instant createdAt;
}
