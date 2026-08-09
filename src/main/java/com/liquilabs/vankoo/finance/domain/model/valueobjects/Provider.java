package com.liquilabs.vankoo.finance.domain.model.valueobjects;

/**
 * Payment providers Finance can route a deposit through. Only Stripe exists
 * in v1 (see {@code infrastructure/providers/stripe}).
 */
public enum Provider {
    STRIPE
}
