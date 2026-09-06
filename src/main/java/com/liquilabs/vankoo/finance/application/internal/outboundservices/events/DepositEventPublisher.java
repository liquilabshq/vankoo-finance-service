package com.liquilabs.vankoo.finance.application.internal.outboundservices.events;

import com.liquilabs.vankoo.finance.domain.model.events.DepositCancelledEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositFailedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositSucceededEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Publishes the deposit outcomes that are public contract.
 *
 * <p><strong>The selection is the absence of methods.</strong> Only the three
 * outcomes have a handler; the four internal events have none, and that is the
 * whole implementation of "decide what gets published". There is no list and no
 * {@code if} to keep in step with the catalogue. Adding a handler for
 * {@code DepositInitiatedEvent} would leak {@code idempotencyKey} and
 * {@code description} across the boundary, which is precisely why it is absent.
 *
 * <p><strong>Its own processing group, deliberately.</strong> Crediting the
 * wallet runs under {@code wallet-crediting}. Keeping them apart means a Kafka
 * outage leaves this token behind, retrying, while deposits keep crediting
 * balances. Sharing a group would let the broker hold up the business, which is
 * the opposite of what publishing-without-a-consumer is worth.
 *
 * <p><strong>It catches nothing, on purpose.</strong> A publication failure has
 * to escape {@code on(...)} for the token to stay put, which is the whole of
 * what keeps an outcome from being lost. That makes it the deliberate opposite
 * of {@code DepositChargeCreator}, which sorts transient failures from permanent
 * ones and swallows the second kind: there, a permanent failure would block its
 * group forever over an event that can never succeed; here every failure is a
 * broker that is not answering <em>yet</em>. Adding a {@code try/catch} for
 * symmetry with that class would undo this.
 *
 * <p>Not catching is necessary but not sufficient: Axon's default
 * {@code LoggingErrorHandler} would catch it for us and let the token advance
 * anyway. {@code IntegrationEventProcessorConfiguration} registers a
 * {@code PropagatingErrorHandler} for this group so that it does not.
 *
 * <p><strong>Careful with token resets.</strong> Resetting this group's token
 * republishes every outcome ever recorded. That is at-least-once working as
 * designed, and the reason the contract makes consumers deduplicate by
 * {@code event-id} — but it is not something to do casually.
 */
@Component
@ProcessingGroup("deposit-integration-events")
public class DepositEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(DepositEventPublisher.class);

    private final EventService eventService;

    public DepositEventPublisher(EventService eventService) {
        this.eventService = eventService;
    }

    @EventHandler
    public void on(DepositSucceededEvent event, EventMessage<?> message) {
        publish(event, event.depositId(), message);
    }

    @EventHandler
    public void on(DepositFailedEvent event, EventMessage<?> message) {
        publish(event, event.depositId(), message);
    }

    @EventHandler
    public void on(DepositCancelledEvent event, EventMessage<?> message) {
        publish(event, event.depositId(), message);
    }

    private void publish(Object payload, String depositId, EventMessage<?> message) {
        IntegrationEvent integrationEvent =
                DepositIntegrationEventAssembler.assemble(payload, depositId, message);
        eventService.publishEvent(integrationEvent);
        LOGGER.info("Published integration event: type={}, depositId={}, eventId={}",
                payload.getClass().getSimpleName(), depositId, message.getIdentifier());
    }
}
