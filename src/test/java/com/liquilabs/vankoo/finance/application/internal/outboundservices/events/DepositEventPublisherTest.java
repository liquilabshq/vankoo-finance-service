package com.liquilabs.vankoo.finance.application.internal.outboundservices.events;

import com.liquilabs.vankoo.finance.domain.exceptions.IntegrationEventPublicationException;
import com.liquilabs.vankoo.finance.domain.model.events.DepositCancelledEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositFailedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositSucceededEvent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Plain unit test, no Spring context: the {@code @EventHandler} methods are
 * called directly and {@link EventService} is mocked.
 *
 * <p>It exists for one assertion, repeated across the three outcomes: a
 * publication failure <strong>escapes</strong> the handler. That is what makes
 * Axon leave the token where it was and offer the event again, so a handler that
 * quietly caught it would drop the outcome — the exact failure this class guards
 * against.
 */
@ExtendWith(MockitoExtension.class)
class DepositEventPublisherTest {

    @Mock
    private EventService eventService;

    private DepositEventPublisher publisher;

    private String depositId;
    private String accountId;

    @BeforeEach
    void setUp() {
        publisher = new DepositEventPublisher(eventService);
        depositId = UUID.randomUUID().toString();
        accountId = UUID.randomUUID().toString();
    }

    @Test
    void aPublicationFailureEscapesTheSucceededHandler() {
        brokerIsDown();
        DepositSucceededEvent event = succeeded();

        assertThrows(IntegrationEventPublicationException.class,
                () -> publisher.on(event, envelope(event)));
    }

    @Test
    void aPublicationFailureEscapesTheFailedHandler() {
        brokerIsDown();
        DepositFailedEvent event =
                new DepositFailedEvent(depositId, accountId, 12_500L, "PEN", "STRIPE", "UNKNOWN");

        assertThrows(IntegrationEventPublicationException.class,
                () -> publisher.on(event, envelope(event)));
    }

    @Test
    void aPublicationFailureEscapesTheCancelledHandler() {
        brokerIsDown();
        DepositCancelledEvent event =
                new DepositCancelledEvent(depositId, accountId, 12_500L, "PEN", "STRIPE", "abandoned");

        assertThrows(IntegrationEventPublicationException.class,
                () -> publisher.on(event, envelope(event)));
    }

    @Test
    void aConfirmedPublicationLetsTheHandlerFinish() {
        DepositSucceededEvent event = succeeded();

        assertDoesNotThrow(() -> publisher.on(event, envelope(event)));
    }

    private void brokerIsDown() {
        doThrow(new IntegrationEventPublicationException("Kafka did not confirm the integration event"))
                .when(eventService).publishEvent(any());
    }

    private DepositSucceededEvent succeeded() {
        return new DepositSucceededEvent(depositId, accountId, 12_500L, "PEN", "STRIPE");
    }

    /** The Axon envelope the assembler reads its identity and timestamp from. */
    private static EventMessage<?> envelope(Object payload) {
        return GenericEventMessage.asEventMessage(payload);
    }
}
