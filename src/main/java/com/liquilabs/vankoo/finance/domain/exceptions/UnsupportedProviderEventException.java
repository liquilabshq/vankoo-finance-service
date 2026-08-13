package com.liquilabs.vankoo.finance.domain.exceptions;

/**
 * The webhook is authentic, but its event type is not one the adapter knows how
 * to normalize into a deposit update.
 *
 * <p>Not retryable, and <strong>not</strong> a fault of the caller: it means our
 * endpoint is subscribed to more event types than we understand, which is a
 * dashboard misconfiguration. It is separate from
 * {@link PaymentProviderRejectedException} so the webhook endpoint can
 * acknowledge it — answering with an error would only make the provider retry an
 * event we will never apply.
 */
public final class UnsupportedProviderEventException extends PaymentProviderException {

    public UnsupportedProviderEventException(String message) {
        super(message);
    }
}
