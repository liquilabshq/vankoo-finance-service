package com.liquilabs.vankoo.finance.infrastructure.configuration;

import com.liquilabs.vankoo.finance.infrastructure.eventstore.axon.CommandRejectionHandlerInterceptor;
import org.axonframework.commandhandling.CommandBus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

/**
 * Puts {@link CommandRejectionHandlerInterceptor} on the handler side of the
 * command bus. Registered by hand, same style as
 * {@link IntegrationEventProcessorConfiguration}, so that whether the
 * interceptor is active never depends on Axon's bean auto-detection rules.
 */
@Configuration
public class CommandBusConfiguration {

    @Autowired
    public void registerCommandRejectionInterceptor(CommandBus commandBus) {
        commandBus.registerHandlerInterceptor(new CommandRejectionHandlerInterceptor());
    }
}
