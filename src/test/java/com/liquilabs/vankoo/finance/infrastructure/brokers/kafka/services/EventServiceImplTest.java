package com.liquilabs.vankoo.finance.infrastructure.brokers.kafka.services;

import com.liquilabs.vankoo.finance.application.internal.outboundservices.events.IntegrationEvent;
import com.liquilabs.vankoo.finance.domain.exceptions.IntegrationEventPublicationException;
import com.liquilabs.vankoo.finance.domain.model.events.DepositSucceededEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit test, no Spring context: {@link StreamBridge} is mocked and
 * {@code publishEvent} is called as a regular Java method.
 *
 * <p>What it pins down is the failure path. The happy path was never in doubt;
 * what was lost before this class existed is the event nobody confirmed.
 */
@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock
    private StreamBridge streamBridge;

    private EventServiceImpl eventService;

    private String depositId;
    private String eventId;
    private IntegrationEvent integrationEvent;

    @BeforeEach
    void setUp() {
        eventService = new EventServiceImpl(streamBridge);

        depositId = UUID.randomUUID().toString();
        eventId = UUID.randomUUID().toString();
        integrationEvent = new IntegrationEvent(
                depositId,
                new DepositSucceededEvent(depositId, UUID.randomUUID().toString(), 12_500L, "PEN", "STRIPE"),
                Map.of(
                        IntegrationEvent.EVENT_TYPE, "DepositSucceededEvent",
                        IntegrationEvent.EVENT_VERSION, "1",
                        IntegrationEvent.EVENT_ID, eventId,
                        IntegrationEvent.AGGREGATE_TYPE, "Deposit",
                        IntegrationEvent.AGGREGATE_ID, depositId,
                        IntegrationEvent.OCCURRED_AT, "2026-09-04T12:00:00Z",
                        IntegrationEvent.CORRELATION_ID, eventId,
                        IntegrationEvent.CONTENT_TYPE, "application/json"));
    }

    @Test
    void aConfirmedSendReturnsNormally() {
        when(streamBridge.send(eq(EventServiceImpl.BINDING), any(Object.class))).thenReturn(true);

        assertDoesNotThrow(() -> eventService.publishEvent(integrationEvent));
    }

    @Test
    void aChannelThatRefusesTheMessageIsAFailure() {
        // No exception, just a false: the message went nowhere all the same.
        when(streamBridge.send(eq(EventServiceImpl.BINDING), any(Object.class))).thenReturn(false);

        IntegrationEventPublicationException thrown = assertThrows(
                IntegrationEventPublicationException.class,
                () -> eventService.publishEvent(integrationEvent));

        assertMessageIdentifiesTheEvent(thrown);
    }

    @Test
    void aBrokerFailureIsRethrownWithItsCause() {
        // What a sync send raises once the broker has stopped answering.
        MessagingException brokerFailure = new MessagingException("the broker is down");
        when(streamBridge.send(eq(EventServiceImpl.BINDING), any(Object.class))).thenThrow(brokerFailure);

        IntegrationEventPublicationException thrown = assertThrows(
                IntegrationEventPublicationException.class,
                () -> eventService.publishEvent(integrationEvent));

        assertSame(brokerFailure, thrown.getCause());
        assertMessageIdentifiesTheEvent(thrown);
    }

    @Test
    void theMessageCarriesTheRecordKeyAndEveryHeaderUntouched() {
        when(streamBridge.send(eq(EventServiceImpl.BINDING), any(Object.class))).thenReturn(true);

        eventService.publishEvent(integrationEvent);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(streamBridge).send(eq(EventServiceImpl.BINDING), captor.capture());
        Message<?> sent = (Message<?>) captor.getValue();

        // The record key, which is what Kafka partitions by — not one more header.
        assertEquals(depositId, sent.getHeaders().get(KafkaHeaders.KEY));
        assertEquals(integrationEvent.payload(), sent.getPayload());
        integrationEvent.headers().forEach((name, value) ->
                assertEquals(value, sent.getHeaders().get(name), "header " + name));
    }

    /** Whoever reads the log has to be able to say which outcome did not make it out. */
    private void assertMessageIdentifiesTheEvent(IntegrationEventPublicationException thrown) {
        assertEquals(
                "Kafka did not confirm the integration event, it must be retried: key=%s, eventId=%s"
                        .formatted(depositId, eventId),
                thrown.getMessage());
    }
}
