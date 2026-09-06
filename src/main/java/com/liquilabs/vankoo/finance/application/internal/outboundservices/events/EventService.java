package com.liquilabs.vankoo.finance.application.internal.outboundservices.events;

/**
 * Outbound port for publishing integration events.
 *
 * <p>Declared here and implemented in {@code infrastructure/brokers/kafka}, so
 * that nothing above infrastructure names a broker. No Kafka type crosses this
 * boundary in either direction.
 */
public interface EventService {

    /**
     * Publishes the event on the integration channel.
     *
     * <p>Delivery is at-least-once: the caller runs inside an Axon processing
     * group, so a failure here leaves the token where it was and the event is
     * offered again. Consumers deduplicate by {@code event-id}, which is why
     * that header comes from the event store and not from this call.
     *
     * <p>That promise is what the {@code throws} below buys. An implementation
     * that returned normally when the broker never confirmed would let the token
     * advance past an event nobody received — so the failure has to reach the
     * caller, and the caller has to let it through.
     *
     * @throws com.liquilabs.vankoo.finance.domain.exceptions.IntegrationEventPublicationException
     *         when the broker does not confirm the event. Always retryable; a
     *         caller that catches it reintroduces the silent loss.
     */
    void publishEvent(IntegrationEvent event);
}
