package com.liquilabs.vankoo.finance.infrastructure.eventstore.axon;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de humo del bootstrap (Tarjeta 2): confirma que la app puede publicar un
 * evento en Axon Server y recuperarlo por replay. No modela ningún caso de uso de
 * negocio — el agregado Deposit real llega en la Tarjeta 3.
 */
@SpringBootTest
class AxonServerEventStoreSmokeTest {

    @Autowired
    private EventStore eventStore;

    @Test
    void publishesAndReplaysATestEventFromAxonServer() {
        String aggregateId = UUID.randomUUID().toString();
        DomainEventMessage<TestEvent> event = new GenericDomainEventMessage<>(
                "BootstrapSmokeTestAggregate", aggregateId, 0L, new TestEvent(aggregateId));

        eventStore.publish(event);

        DomainEventStream stream = eventStore.readEvents(aggregateId);
        assertThat(stream.hasNext()).isTrue();
        TestEvent replayed = (TestEvent) stream.next().getPayload();
        assertThat(replayed.aggregateId()).isEqualTo(aggregateId);
        assertThat(stream.hasNext()).isFalse();
    }

    record TestEvent(String aggregateId) {
    }
}
