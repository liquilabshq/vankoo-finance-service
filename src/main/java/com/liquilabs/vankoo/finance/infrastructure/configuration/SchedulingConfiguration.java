package com.liquilabs.vankoo.finance.infrastructure.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the scheduler that drives the webhook inbox sweep.
 *
 * <p>It lives here, and not on the application class, because scheduling is
 * technical plumbing of one component rather than a property of the service as
 * a whole.
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
