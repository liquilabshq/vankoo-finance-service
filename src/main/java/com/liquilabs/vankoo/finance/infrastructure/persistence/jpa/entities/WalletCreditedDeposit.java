package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.DepositIdConverter;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.WalletIdConverter;
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
 * One row per deposit that already credited a wallet. Maps
 * {@code finance_ops.wallet_credited_deposits} — the deduplication barrier
 * {@code WalletCreditor} checks before dispatching {@code CreditWalletCommand}.
 */
@Entity
@Table(schema = "finance_ops")
@Getter
@NoArgsConstructor
public class WalletCreditedDeposit {

    @Id
    private UUID id;

    @Convert(converter = WalletIdConverter.class)
    @Column(nullable = false)
    private WalletId walletId;

    @Convert(converter = DepositIdConverter.class)
    @Column(nullable = false)
    private DepositId depositId;

    @Column(nullable = false)
    private Instant creditedAt;
}
