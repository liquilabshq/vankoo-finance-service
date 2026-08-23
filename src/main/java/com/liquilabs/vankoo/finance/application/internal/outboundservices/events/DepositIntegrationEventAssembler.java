package com.liquilabs.vankoo.finance.application.internal.outboundservices.events;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.correlation.MessageOriginProvider;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns a deposit domain event into the message the integration contract
 * describes.
 *
 * <p>This is the whole of the "mapping" the contract asks for, and it maps
 * <strong>around</strong> the payload, never through it: key and headers are
 * built here, the domain event travels as-is. Trimming a field would break
 * ADR-0001, under which a domain event and its integration event share one
 * payload.
 *
 * <p>Static, following the project's assembler convention.
 */
public final class DepositIntegrationEventAssembler {

    /** Only value of {@code aggregate-type} in v1: every published fact belongs to a deposit. */
    private static final String AGGREGATE_TYPE_DEPOSIT = "Deposit";

    private static final String SCHEMA_VERSION = "1";

    private static final String JSON = "application/json";

    private DepositIntegrationEventAssembler() {
    }

    /**
     * @param payload    the domain event, published unchanged
     * @param depositId  the record key — everything about one deposit shares it,
     *                   so Kafka keeps its outcomes in causal order
     * @param message    the Axon envelope, which already carries the identity,
     *                   the timestamp and the correlation metadata
     */
    public static IntegrationEvent assemble(Object payload, String depositId, EventMessage<?> message) {
        MetaData metaData = message.getMetaData();

        Map<String, String> headers = new HashMap<>();
        headers.put(IntegrationEvent.EVENT_TYPE, payload.getClass().getSimpleName());
        headers.put(IntegrationEvent.EVENT_VERSION, SCHEMA_VERSION);
        // From the event store, not generated here: that is what keeps it stable
        // across producer retries, which is what lets a consumer deduplicate.
        headers.put(IntegrationEvent.EVENT_ID, message.getIdentifier());
        headers.put(IntegrationEvent.AGGREGATE_TYPE, AGGREGATE_TYPE_DEPOSIT);
        headers.put(IntegrationEvent.AGGREGATE_ID, depositId);
        headers.put(IntegrationEvent.OCCURRED_AT,
                DateTimeFormatter.ISO_INSTANT.format(message.getTimestamp()));
        headers.put(IntegrationEvent.CORRELATION_ID, correlationId(metaData, message));
        headers.put(IntegrationEvent.CONTENT_TYPE, JSON);

        causationId(metaData).ifPresent(id -> headers.put(IntegrationEvent.CAUSATION_ID, id));

        return new IntegrationEvent(depositId, payload, headers);
    }

    /**
     * The contract's {@code correlation-id} identifies the whole business
     * operation, which is Axon's <strong>traceId</strong> — the message that
     * started the chain. Axon's own {@code correlationId} means something else
     * (the message that caused this one), so translating the two by name would
     * swap them.
     *
     * <p>Falls back to the event's own identifier when there is no trace: an
     * event with nothing upstream is the start of its own chain. The header is
     * mandatory, and stalling the processor over it would stop every later
     * event from being published.
     */
    private static String correlationId(MetaData metaData, EventMessage<?> message) {
        Object traceId = metaData.get(MessageOriginProvider.getDefaultTraceKey());
        return traceId == null ? message.getIdentifier() : traceId.toString();
    }

    /** Axon's {@code correlationId}: the message that caused this one. Optional. */
    private static java.util.Optional<String> causationId(MetaData metaData) {
        Object causationId = metaData.get(MessageOriginProvider.getDefaultCorrelationKey());
        return java.util.Optional.ofNullable(causationId).map(Object::toString);
    }
}
