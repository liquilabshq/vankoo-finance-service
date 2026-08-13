package com.liquilabs.vankoo.finance.domain.exceptions;

/**
 * Root of the payment port's errors. The Stripe adapter translates SDK errors
 * into these types, so that no provider exception ever crosses the port.
 *
 * <p>It is {@code sealed} on purpose: a new error type has to be declared here
 * instead of leaking as a loose {@code RuntimeException} out of
 * {@code infrastructure/providers/stripe}. Because the project has no
 * {@code module-info.java}, every permitted subclass must live in this same
 * package.
 *
 * <p>Unlike the rest of this package, these are not thrown by the aggregate:
 * they describe a conversation with an external system, and no domain event
 * follows from one. A declined charge is <em>not</em> here — that is a business
 * outcome and travels as {@link
 * com.liquilabs.vankoo.finance.domain.model.valueobjects.NormalizedDepositStatus#FAILED}.
 */
public abstract sealed class PaymentProviderException extends RuntimeException
        permits PaymentProviderTimeoutException,
        PaymentProviderUnavailableException,
        PaymentProviderRejectedException,
        InvalidWebhookSignatureException,
        UnsupportedProviderEventException {

    protected PaymentProviderException(String message) {
        super(message);
    }

    protected PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
