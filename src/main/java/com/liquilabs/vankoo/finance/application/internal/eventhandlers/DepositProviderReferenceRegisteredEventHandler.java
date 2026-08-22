package com.liquilabs.vankoo.finance.application.internal.eventhandlers;

import com.liquilabs.vankoo.finance.domain.model.events.DepositProviderReferenceRegisteredEvent;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories.DepositProviderReferenceRepository;
import com.fasterxml.uuid.Generators;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Keeps {@code finance_ops.deposit_provider_references} in step with the
 * aggregate, so the webhook inbox can tell which deposit an external event
 * belongs to.
 *
 * <p>It is the other half of the early-arrival race: this handler and the
 * provider's callback are two writers with no ordering between them. Whichever
 * loses, the inbox parks and retries until this row exists.
 *
 * <p><strong>Why it sits in {@code application/internal/eventhandlers}:</strong>
 * it reacts to one of our own events and writes neither the read model nor an
 * outbound message. It is not {@code queryservices} — that package is the Query
 * Model, and this table is not read model: it takes part in an admission
 * decision, which ADR-0001 forbids doing against a projection. Nor is it
 * {@code interfaces/messaging/eventhandlers}, which is for events from
 * <em>other</em> bounded contexts.
 */
@Component
@ProcessingGroup("deposit-provider-reference")
public class DepositProviderReferenceRegisteredEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DepositProviderReferenceRegisteredEventHandler.class);

    private final DepositProviderReferenceRepository referenceRepository;

    public DepositProviderReferenceRegisteredEventHandler(DepositProviderReferenceRepository referenceRepository) {
        this.referenceRepository = referenceRepository;
    }

    /**
     * @param registeredAt when the event happened, not when it was handled — a
     *                     replay must reproduce the same row it wrote the first time
     */
    @EventHandler
    public void on(DepositProviderReferenceRegisteredEvent event, @Timestamp Instant registeredAt) {
        int inserted = referenceRepository.insertIfAbsent(
                Generators.timeBasedEpochGenerator().generate(),
                event.provider(),
                event.providerDepositId(),
                UUID.fromString(event.depositId()),
                registeredAt);

        if (inserted == 0) {
            LOGGER.debug("Provider reference already registered, ignoring: provider={}, providerDepositId={}",
                    event.provider(), event.providerDepositId());
        }
    }
}
