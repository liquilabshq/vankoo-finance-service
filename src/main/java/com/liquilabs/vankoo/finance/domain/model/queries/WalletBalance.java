package com.liquilabs.vankoo.finance.domain.model.queries;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;

import java.time.Instant;

/**
 * What the business asks about a wallet's balance — the Read Model row
 * translated into value objects, never the JPA {@code @Entity} itself.
 *
 * <p>Deliberately excludes {@code lastEventId} and {@code projectionVersion}:
 * projection bookkeeping, not something a query caller should ever see.
 */
public record WalletBalance(
        WalletId walletId,
        AccountId accountId,
        Money balance,
        Instant createdAt,
        Instant updatedAt) {
}
