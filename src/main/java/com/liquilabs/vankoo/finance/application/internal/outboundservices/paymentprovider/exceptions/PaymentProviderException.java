package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.exceptions;

/**
 * Root of the payment port's errors. The concrete adapter translates SDK errors
 * into these types: neither {@code application} nor {@code domain} ever sees a
 * Stripe exception.
 *
 * <p>It is {@code sealed} on purpose: if Tarjeta 6 needs a new error type, the
 * compiler forces it to be declared in this file instead of leaving a loose
 * {@code RuntimeException} in {@code infrastructure/providers/stripe}.
 *
 * <p>Without a {@code module-info.java}, Java requires the {@code permits}
 * classes to live in this very package. That is why the whole hierarchy sits
 * together.
 */
public sealed abstract class PaymentProviderException extends RuntimeException
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
