package com.liquilabs.vankoo.finance.domain.model.events;

import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.SimpleSerializedType;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code WalletDebitedEvent} is persisted in Axon Server. Adding {@code debitId}
 * must not break loading the events stored before it existed — this pins that
 * down with the same serializer the service runs on
 * ({@code axon.serializer.general: jackson}).
 */
class WalletDebitedEventCompatibilityTest {

    private final JacksonSerializer serializer = JacksonSerializer.defaultSerializer();

    @Test
    void eventStoredBeforeDebitIdExisted_deserializesWithNullDebitId() {
        String legacyPayload = "{\"walletId\":\"w-1\",\"amountMinor\":5000,\"currency\":\"PEN\",\"reason\":\"INVERSION\"}";

        WalletDebitedEvent event = serializer.deserialize(new SimpleSerializedObject<>(
                legacyPayload.getBytes(StandardCharsets.UTF_8),
                byte[].class,
                new SimpleSerializedType(WalletDebitedEvent.class.getName(), null)));

        assertEquals("w-1", event.walletId());
        assertEquals(5_000L, event.amountMinor());
        assertEquals("INVERSION", event.reason());
        assertNull(event.debitId());
    }

    @Test
    void eventWithDebitId_roundTrips() {
        WalletDebitedEvent original = new WalletDebitedEvent("w-1", 5_000L, "PEN", "INVERSION", "d-1");

        WalletDebitedEvent restored = serializer.deserialize(serializer.serialize(original, byte[].class));

        assertEquals(original, restored);
    }
}
