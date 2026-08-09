package com.liquilabs.vankoo.finance.domain.model.valueobjects;

/**
 * Money as a flat integer amount in minor units plus its currency.
 *
 * <p>{@code amountMinor > 0} is <strong>not</strong> enforced here on purpose:
 * the {@code Deposit} aggregate checks it in its {@code @CommandHandler}
 * constructor, literally as the course guide's example does (see ADR-0002),
 * so {@link com.liquilabs.vankoo.finance.domain.model.exceptions.InvalidDepositAmountException}
 * stays a real, reachable business rule instead of dead code.
 */
public record Money(long amountMinor, Currency currency) {

    public Money {
        if (currency == null) {
            throw new IllegalArgumentException("currency must not be null");
        }
    }

    /**
     * @throws IllegalArgumentException if {@code other} is in a different
     *         currency — this is arithmetic, not a business invariant, so it
     *         is not one of the domain exceptions.
     */
    public Money add(Money other) {
        if (this.currency != other.currency) {
            throw new IllegalArgumentException(
                    "Cannot add Money of different currencies: " + this.currency + " and " + other.currency);
        }
        return new Money(this.amountMinor + other.amountMinor, this.currency);
    }
}
