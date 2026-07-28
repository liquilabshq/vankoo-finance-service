package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.exceptions;

/**
 * The provider is down or returned a server error. Retryable.
 */
public final class PaymentProviderUnavailableException extends PaymentProviderException
        implements RetryablePaymentProviderException {

    public PaymentProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
