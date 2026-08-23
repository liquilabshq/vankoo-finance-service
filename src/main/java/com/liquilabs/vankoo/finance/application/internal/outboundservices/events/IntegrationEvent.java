package com.liquilabs.vankoo.finance.application.internal.outboundservices.events;

import java.util.Map;
import java.util.Set;

/**
 * A public business fact on its way out of the bounded context, with everything
 * the broker contract requires around it.
 *
 * <p>The {@code payload} is the domain event itself, untouched. ADR-0001 fixes
 * that a domain event and its integration event share the same payload, and
 * there is no mapper trimming fields — which is exactly why the four internal
 * events are not published at all rather than published with fields removed.
 *
 * <p>{@code key} is what Kafka partitions by. Everything about one deposit has
 * to carry the same key, or a consumer could see its outcome before its start.
 *
 * <p>The compact constructor demands the mandatory headers <em>here</em>, at
 * publication time, on purpose. Kafka has no consumer in v1, so a missing header
 * breaks nothing today: it would surface months from now, with a topic full of
 * badly labelled history behind it.
 */
public record IntegrationEvent(String key, Object payload, Map<String, String> headers) {

    public static final String EVENT_TYPE = "event-type";
    public static final String EVENT_VERSION = "event-version";
    public static final String EVENT_ID = "event-id";
    public static final String AGGREGATE_TYPE = "aggregate-type";
    public static final String AGGREGATE_ID = "aggregate-id";
    public static final String OCCURRED_AT = "occurred-at";
    public static final String CORRELATION_ID = "correlation-id";
    public static final String CONTENT_TYPE = "content-type";

    /** {@code causation-id} is the only optional one: the first message of a chain has no cause. */
    public static final String CAUSATION_ID = "causation-id";

    private static final Set<String> MANDATORY_HEADERS = Set.of(
            EVENT_TYPE, EVENT_VERSION, EVENT_ID, AGGREGATE_TYPE,
            AGGREGATE_ID, OCCURRED_AT, CORRELATION_ID, CONTENT_TYPE);

    public IntegrationEvent {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required: it is what Kafka partitions by");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
        if (headers == null) {
            throw new IllegalArgumentException("headers are required");
        }
        for (String mandatory : MANDATORY_HEADERS) {
            String value = headers.get(mandatory);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Mandatory header is missing or blank: " + mandatory);
            }
        }
        headers = Map.copyOf(headers);
    }
}
