package com.liquilabs.vankoo.finance.domain.model.valueobjects;

import java.util.UUID;

/**
 * Identity of an auction in Investment, as Finance sees it: only a reference.
 * Finance never mints one — it arrives on {@code investment.auction-lifecycle.v1}
 * as the event's {@code aggregateId} — so there is no generating constructor,
 * unlike {@link DepositId}.
 */
public record AuctionId(UUID value) {

    public AuctionId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }

    public static AuctionId of(String value) {
        return new AuctionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
