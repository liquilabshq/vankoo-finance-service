package com.liquilabs.vankoo.finance.domain.exceptions;

/**
 * The broker did not confirm an integration event.
 *
 * <p><strong>Always retryable.</strong> Unlike {@link PaymentProviderException},
 * this hierarchy has no permanent branch to swallow: the point of throwing at all
 * is that the caller's Axon processor leaves its token where it was and offers
 * the event again. Catching one and carrying on would restore exactly the silent
 * loss this type exists to prevent.
 *
 * <p>Not sealed and with no subclasses, because there is one failure mode: the
 * event did not reach the topic. Why it did not — a timeout, too few in-sync
 * replicas, a broker that is down — travels in the cause, and no caller branches
 * on it.
 *
 * <p>Lives here for the same reason {@code PaymentProviderException} does: it is
 * a port's failure contract, thrown from {@code infrastructure} and never by the
 * aggregate. See the contract's «{@code domain/exceptions}, no
 * {@code domain/model/exceptions}» section.
 */
public class IntegrationEventPublicationException extends RuntimeException {

    public IntegrationEventPublicationException(String message) {
        super(message);
    }

    public IntegrationEventPublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
