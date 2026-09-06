package com.liquilabs.vankoo.finance.infrastructure.brokers.kafka.services;

import com.liquilabs.vankoo.finance.application.internal.outboundservices.events.IntegrationEvent;
import com.liquilabs.vankoo.finance.domain.exceptions.IntegrationEventPublicationException;
import com.liquilabs.vankoo.finance.infrastructure.brokers.kafka.KafkaEventService;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * Sends integration events through the Kafka binding.
 *
 * <p>Transport only, with one decision of its own: <strong>a send that the broker
 * does not confirm is an error, and it is thrown.</strong> The payload and every
 * header arrive already assembled, so this class still never needs to know a
 * single domain event.
 *
 * <p><strong>Why the throw matters.</strong> The binding sets {@code sync: true},
 * so the binder waits for the broker's acknowledgement and raises whatever went
 * wrong. Without it the binder publishes asynchronously: {@code send()} returns
 * as soon as the record reaches the producer, and a later rejection — a timeout,
 * {@code acks=all} without enough in-sync replicas, a broker that is down — is
 * handed to a failure channel that is {@code null} unless {@code
 * errorChannelEnabled} is on. Nothing reads it, the caller's {@code @EventHandler}
 * returns cleanly, Axon advances the token, and the outcome is gone for good.
 *
 * <p>The {@code sync} flag alone is not the guarantee, though, which is why the
 * returned {@code boolean} is checked here too: it lives in configuration, in a
 * namespace that has already been wrong once, and a channel can refuse a message
 * without throwing.
 */
@Service
public class EventServiceImpl implements KafkaEventService {

    /**
     * Logical binding name. It must match {@code spring.cloud.stream.bindings}
     * in the profile configuration; there is no compile-time link between the
     * two, so a rename in either place has to be made in both.
     */
    static final String BINDING = "finance-out-0";

    private final StreamBridge streamBridge;

    public EventServiceImpl(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @Override
    public void publishEvent(IntegrationEvent event) {
        MessageBuilder<Object> builder = MessageBuilder.withPayload(event.payload())
                // The record key, not just a header: this is what Kafka partitions
                // by, and it needs key.serializer to be the String one — the
                // binder's default serializes keys as byte[].
                .setHeader(KafkaHeaders.KEY, event.key());

        event.headers().forEach(builder::setHeader);

        Message<Object> message = builder.build();

        boolean sent;
        try {
            sent = streamBridge.send(BINDING, message);
        } catch (RuntimeException exception) {
            throw new IntegrationEventPublicationException(failureMessage(event), exception);
        }
        if (!sent) {
            // The channel refused the message without raising. Just as lost as a
            // rejection from the broker, so it gets the same treatment.
            throw new IntegrationEventPublicationException(failureMessage(event));
        }
    }

    /** Key and {@code event-id}: enough to say exactly which outcome did not make it out. */
    private static String failureMessage(IntegrationEvent event) {
        return "Kafka did not confirm the integration event, it must be retried: key=%s, eventId=%s"
                .formatted(event.key(), event.headers().get(IntegrationEvent.EVENT_ID));
    }
}
