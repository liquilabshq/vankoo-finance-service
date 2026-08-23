package com.liquilabs.vankoo.finance.infrastructure.brokers.kafka.services;

import com.liquilabs.vankoo.finance.application.internal.outboundservices.events.IntegrationEvent;
import com.liquilabs.vankoo.finance.infrastructure.brokers.kafka.KafkaEventService;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * Sends integration events through the Kafka binding.
 *
 * <p>Transport only. It decides nothing about what is published or how it is
 * labelled — the payload and every header arrive already assembled, so this
 * class never needs to know a single domain event.
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
        streamBridge.send(BINDING, message);
    }
}
