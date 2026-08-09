package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.exceptions;

/**
 * Root of the payment port's errors. The adapter translates SDK errors into
 * these types: neither {@code application} nor {@code domain} ever sees a
 * Stripe exception.
 *
 * <p>It is {@code sealed} on purpose: a new error type has to be declared
 * here instead of leaking as a loose {@code RuntimeException} out of
 * {@code infrastructure/providers/stripe}.
 */
public abstract sealed class PaymentProviderException extends RuntimeException
        permits PaymentProviderTimeoutException,
                PaymentProviderUnavailableException,
                PaymentProviderRejectedException,
                InvalidWebhookSignatureException {

    protected PaymentProviderException(String message) {
        super(message);
    }

    protected PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
