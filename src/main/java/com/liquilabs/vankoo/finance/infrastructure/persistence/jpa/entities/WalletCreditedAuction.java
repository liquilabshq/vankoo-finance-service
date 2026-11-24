package com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AuctionId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.converters.AuctionIdConverter;
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
 * One row per funded auction whose advance already reached the MYPE's wallet.
 * Maps {@code finance_ops.wallet_credited_auctions} — the deduplication barrier
 * {@code AuctionFundingCreditor} checks before dispatching
 * {@code CreditWalletFromAuctionCommand}. Twin of {@link WalletCreditedDeposit}.
 */
@Entity
@Table(schema = "finance_ops")
@Getter
@NoArgsConstructor
public class WalletCreditedAuction {

    @Id
    private UUID id;

    @Convert(converter = WalletIdConverter.class)
    @Column(nullable = false)
    private WalletId walletId;

    @Convert(converter = AuctionIdConverter.class)
    @Column(nullable = false, unique = true)
    private AuctionId auctionId;

    @Column(nullable = false)
    private Instant creditedAt;
}
