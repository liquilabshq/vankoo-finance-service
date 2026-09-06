package com.liquilabs.vankoo.finance.infrastructure.brokers.kafka.services;

import com.liquilabs.vankoo.finance.application.internal.outboundservices.events.EventService;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.events.IntegrationEvent;
import com.liquilabs.vankoo.finance.domain.exceptions.IntegrationEventPublicationException;
import com.liquilabs.vankoo.finance.domain.model.events.DepositSucceededEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The card's acceptance criterion, automated: simulate a Kafka outage during
 * publication and prove the event is never lost in silence.
 *
 * <p><strong>The broker has to die mid-flight.</strong> A broker that was never
 * there is a different failure: the producer never gets metadata and
 * {@code send()} raises on {@code max.block.ms}, which the old code would also
 * have surfaced. The silent loss needs a producer that already has metadata,
 * takes the record into its buffer, and only fails afterwards — so the test
 * publishes once successfully first, and only then takes the broker away.
 *
 * <p><strong>Paused, not stopped.</strong> Pausing freezes the broker while
 * keeping the container and its published port, which is what lets the same
 * binder recover afterwards; a stopped container would come back on a different
 * port and the recovery half of the test would be untestable. It also models the
 * more honest outage: a broker that is still there and no longer answering.
 *
 * <p><strong>It runs against the real profile configuration</strong>
 * ({@code @ActiveProfiles("dev")}), only overriding the brokers address. That is
 * deliberate: what broke here was configuration — {@code sync}, the namespace it
 * lives in, and the producer timeouts — so a test that declared its own binding
 * would pass while production stayed broken.
 *
 * <p>Only the Kafka half of the app is started. Whether the Axon token holds is
 * the {@code deposit-integration-events} processor's behaviour, covered by
 * {@code DepositEventPublisherTest} at unit level and by the manual procedure in
 * the contract; bringing Postgres and Axon Server up here would buy that at the
 * cost of a three-container test.
 */
@SpringBootTest(
        classes = KafkaOutageIntegrationTest.KafkaOnlyApplication.class,
        properties = {
                // Neither is part of this test, and leaving them on would make it
                // behave differently depending on what happens to be running on the
                // developer's machine.
                "eureka.client.enabled=false",
                "axon.axonserver.enabled=false"
        })
@ActiveProfiles("dev")
@Testcontainers
class KafkaOutageIntegrationTest {

    /** Same image as the compose stack, so the test and dev meet the same broker. */
    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.1"));

    @DynamicPropertySource
    static void brokerAddress(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.stream.kafka.binder.brokers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private EventService eventService;

    @Test
    void aBrokerThatStopsAnsweringMidFlightFailsThePublicationInsteadOfSwallowingIt() {
        // Warm-up. Also the control: if this throws, the binding itself is broken
        // and the rest of the test would be meaningless.
        assertDoesNotThrow(() -> eventService.publishEvent(anOutcome()));

        pause();
        try {
            IntegrationEventPublicationException thrown = assertThrows(
                    IntegrationEventPublicationException.class,
                    () -> eventService.publishEvent(anOutcome()),
                    "the publication has to fail loudly; returning normally is the silent loss");
            assertNotNull(thrown.getCause(), "the broker's own failure has to travel in the cause");
        } finally {
            unpause();
        }

        // And the event becomes publishable again once the broker answers, which
        // is what makes «pending retry» different from «lost».
        assertDoesNotThrow(() -> eventService.publishEvent(anOutcome()));
    }

    private static void pause() {
        DockerClientFactory.instance().client()
                .pauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    private static void unpause() {
        DockerClientFactory.instance().client()
                .unpauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    private static IntegrationEvent anOutcome() {
        String depositId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        return new IntegrationEvent(
                depositId,
                new DepositSucceededEvent(depositId, UUID.randomUUID().toString(), 12_500L, "PEN", "STRIPE"),
                Map.of(
                        IntegrationEvent.EVENT_TYPE, "DepositSucceededEvent",
                        IntegrationEvent.EVENT_VERSION, "1",
                        IntegrationEvent.EVENT_ID, eventId,
                        IntegrationEvent.AGGREGATE_TYPE, "Deposit",
                        IntegrationEvent.AGGREGATE_ID, depositId,
                        IntegrationEvent.OCCURRED_AT, DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                        IntegrationEvent.CORRELATION_ID, eventId,
                        IntegrationEvent.CONTENT_TYPE, "application/json"));
    }

    /**
     * Just enough application to own a Kafka binding: the port's implementation
     * plus Spring Cloud Stream. Everything that would demand Postgres, Axon
     * Server or Eureka is switched off — this test is about the broker.
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
            org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration.class,
            org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration.class
    })
    @Import(EventServiceImpl.class)
    static class KafkaOnlyApplication {
    }
}
