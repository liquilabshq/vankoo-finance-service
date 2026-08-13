package com.liquilabs.vankoo.finance.infrastructure.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Tuning of the webhook inbox sweep.
 *
 * <p>{@code pollInterval} is read by {@code @Scheduled} through a property
 * placeholder, not from this bean: annotation values are resolved before any
 * bean exists. Both point at the same key, so there is still one source of
 * truth in {@code application.yaml}.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "vankoo.finance.webhook-inbox")
public class WebhookInboxProperties {

    /** How often the sweep runs. Also the worst-case latency of a webhook. */
    private Duration pollInterval = Duration.ofSeconds(2);

    /** Rows taken per sweep. Small: each one holds a row lock while it works. */
    private int batchSize = 20;

    /**
     * Attempts before a row stops being retried and waits for a human. Twelve
     * with the default backoff covers a little over an hour, which is far more
     * than the gap between our own write and the provider's callback.
     */
    private int maxAttempts = 12;

    /** First retry delay. Doubles on every attempt, up to {@link #backoffMax}. */
    private Duration backoffBase = Duration.ofSeconds(5);

    private Duration backoffMax = Duration.ofMinutes(10);
}
