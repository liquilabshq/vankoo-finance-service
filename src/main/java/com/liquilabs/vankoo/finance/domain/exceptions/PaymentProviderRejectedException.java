package com.liquilabs.vankoo.finance.domain.exceptions;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.NormalizedDepositStatus;

/**
 * The provider definitively rejected the request: malformed request, bad
 * credentials, or a provider-side rule that is not met.
 *
 * <p>Not retryable. Note this is not the same as a declined charge: a charge
 * that fails is a business outcome and travels as
 * {@link NormalizedDepositStatus#FAILED}, not as an exception.
 */
public final class PaymentProviderRejectedException extends PaymentProviderException {

    public PaymentProviderRejectedException(String message) {
        super(message);
    }

    public PaymentProviderRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
