package com.liquilabs.vankoo.finance.domain.model.valueobjects;

/**
 * Finance-normalized reason a deposit failed. Provider-internal error codes
 * never cross the port boundary as-is — they are mapped to one of these.
 */
public enum FailureReason {
    DECLINED,
    EXPIRED,
    INVALID_PAYMENT_METHOD,
    PROVIDER_ERROR,
    UNKNOWN
}
