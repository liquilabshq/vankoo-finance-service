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
     */
    void publishEvent(IntegrationEvent event);
}
