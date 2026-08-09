package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.exceptions;

/**
 * The provider did not answer within the timeout configured by the adapter.
 *
 * <p>Retryable, but the retry must keep the same {@code IdempotencyKey}: a
 * timeout is no proof that the operation did not run on the other side.
 */
public final class PaymentProviderTimeoutException extends PaymentProviderException
        implements RetryablePaymentProviderException {

    public PaymentProviderTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
