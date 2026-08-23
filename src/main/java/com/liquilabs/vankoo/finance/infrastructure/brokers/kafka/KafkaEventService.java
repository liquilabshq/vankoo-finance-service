package com.liquilabs.vankoo.finance.infrastructure.brokers.kafka;

import com.liquilabs.vankoo.finance.application.internal.outboundservices.events.EventService;

/**
 * Kafka's side of the {@link EventService} port. It adds nothing: it exists so
 * the binding between the port and this technology is a named type rather than
 * an implicit one, matching how the rest of Vankoo wires its brokers.
 */
public interface KafkaEventService extends EventService {
}
