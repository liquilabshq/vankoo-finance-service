package com.liquilabs.vankoo.finance.infrastructure.eventstore.axon;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves Axon Server's optimistic concurrency check: an aggregate's events must
 * be appended with a strictly increasing sequence number, and any second writer
 * that tries to append at a sequence already taken is rejected — never silently
 * overwritten or interleaved.
 *
 * <p>This is exercised directly against {@link EventStore}, the same level
 * {@link AxonServerEventStoreSmokeTest} already uses, and not through
 * {@code Deposit} or its {@code CommandGateway}: Axon's default repository
 * locking is pessimistic and per-JVM, so two commands dispatched in the same
 * process would simply queue for the same lock instead of ever racing for a
 * sequence number. The conflict this test proves is the one that actually
 * matters — the storage-level guarantee that holds even across separate
 * application instances, which no in-process lock can provide.
 */
@SpringBootTest
class AggregateOptimisticConcurrencyTest {

    @Autowired
    private EventStore eventStore;

    record TestEvent(String note) {
    }

    @Test
    void rejectsASecondWriterThatTargetsAnAlreadyTakenSequenceNumber() {
        String aggregateId = UUID.randomUUID().toString();

        eventStore.publish(domainEvent(aggregateId, 0L, "created"));
        eventStore.publish(domainEvent(aggregateId, 1L, "first writer"));

        // A second writer that also believed sequence 1 was free — e.g. it loaded
        // the aggregate before the first writer's commit — must be rejected, not
        // accepted alongside or after it.
        assertThatThrownBy(() -> eventStore.publish(domainEvent(aggregateId, 1L, "second writer")))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(exception -> {
                    StatusRuntimeException grpcException = (StatusRuntimeException) exception;
                    assertThat(grpcException.getStatus().getCode()).isEqualTo(Status.Code.OUT_OF_RANGE);
                    assertThat(grpcException.getMessage()).contains("Invalid sequence number");
                });
    }

    private DomainEventMessage<TestEvent> domainEvent(String aggregateId, long sequenceNumber, String note) {
        return new GenericDomainEventMessage<>("Deposit", aggregateId, sequenceNumber, new TestEvent(note));
    }
}
