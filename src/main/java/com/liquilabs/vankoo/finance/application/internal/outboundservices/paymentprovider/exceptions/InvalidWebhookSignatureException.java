package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.exceptions;

/**
 * The webhook signature does not validate against the provider's secret.
 *
 * <p>Not retryable: the payload is not authentic and must reach neither the
 * inbox nor a command.
 */
public final class InvalidWebhookSignatureException extends PaymentProviderException {

    public InvalidWebhookSignatureException(String message) {
        super(message);
    }
}
