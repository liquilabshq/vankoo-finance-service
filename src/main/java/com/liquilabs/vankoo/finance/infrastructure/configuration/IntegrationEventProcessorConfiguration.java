package com.liquilabs.vankoo.finance.infrastructure.configuration;

import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

/**
 * Makes a failed publication actually stop the {@code deposit-integration-events}
 * processor.
 *
 * <p><strong>Why this class has to exist.</strong> Axon's default
 * {@code ListenerInvocationErrorHandler} is {@link
 * org.axonframework.eventhandling.LoggingErrorHandler}: it catches whatever the
 * handler throws, logs «failed to handle event ... Continuing processing with
 * next listener», and returns normally. The processor then sees a batch that
 * went fine and advances its token. Verified against a real broker — the token
 * moved from 12 to 15 while the event never reached Kafka — so throwing from
 * {@code EventServiceImpl} buys nothing on its own.
 *
 * <p>{@link PropagatingErrorHandler} lets the exception reach the processor's own
 * {@code ErrorHandler}, which does leave the token where it was and retries the
 * batch with a backoff. That is the behaviour {@code EventService} promises and
 * {@code DepositEventPublisher} documents.
 *
 * <p><strong>Scoped to this group on purpose.</strong> Every other processing
 * group keeps the default. {@code deposit-charge-creation} and
 * {@code wallet-crediting} rethrow transient failures expecting the same
 * treatment and are not getting it either, but changing them means deciding what
 * an unpublishable event does to <em>their</em> queues, which belongs to their
 * own cards, not this one.
 */
@Configuration
public class IntegrationEventProcessorConfiguration {

    /** Must match the {@code @ProcessingGroup} on {@code DepositEventPublisher}. */
    private static final String INTEGRATION_EVENTS = "deposit-integration-events";

    @Autowired
    public void configureErrorHandling(EventProcessingConfigurer configurer) {
        configurer.registerListenerInvocationErrorHandler(
                INTEGRATION_EVENTS, configuration -> PropagatingErrorHandler.instance());
    }
}
