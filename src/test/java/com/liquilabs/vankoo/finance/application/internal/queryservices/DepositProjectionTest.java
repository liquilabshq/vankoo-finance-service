package com.liquilabs.vankoo.finance.application.internal.queryservices;

import com.liquilabs.vankoo.finance.domain.model.events.DepositActionRequiredEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositCancelledEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositFailedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositInitiatedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositProcessingStartedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositProviderReferenceRegisteredEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositSucceededEvent;
import com.liquilabs.vankoo.finance.domain.model.queries.DepositSummary;
import com.liquilabs.vankoo.finance.domain.model.queries.DepositSummaryPage;
import com.liquilabs.vankoo.finance.domain.model.queries.GetDepositByIdQuery;
import com.liquilabs.vankoo.finance.domain.model.queries.ListDepositsByAccountQuery;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositStatus;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.FailureReason;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.DepositViewEntity;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories.DepositViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit test, no Spring context: the {@code @EventHandler} methods are
 * called directly as regular Java methods, and {@link DepositViewRepository}
 * is mocked. Axon's own message dispatch and replay machinery are not under
 * test here — only that this projection turns events into the right row
 * mutations, idempotently. End-to-end replay against a real event store is
 * covered by the separate "Pruebas de integración" card.
 */
@ExtendWith(MockitoExtension.class)
class DepositProjectionTest {

    @Mock
    private DepositViewRepository repository;

    private DepositProjection projection;

    private String depositId;
    private String accountId;

    @BeforeEach
    void setUp() {
        projection = new DepositProjection(repository);
        depositId = UUID.randomUUID().toString();
        accountId = UUID.randomUUID().toString();
    }

    @Test
    void initiatedEvent_createsRow() {
        when(repository.existsByDepositId(DepositId.of(depositId))).thenReturn(false);
        Instant occurredAt = Instant.now();

        projection.on(
                new DepositInitiatedEvent(depositId, accountId, 12500L, "PEN", "STRIPE", "idem-1", "Recarga inicial"),
                "evt-1", occurredAt);

        ArgumentCaptor<DepositViewEntity> saved = ArgumentCaptor.forClass(DepositViewEntity.class);
        verify(repository).save(saved.capture());
        DepositViewEntity row = saved.getValue();

        assertEquals(DepositId.of(depositId), row.getDepositId());
        assertEquals(AccountId.of(accountId), row.getAccountId());
        assertEquals(12500L, row.getAmountMinor());
        assertEquals(DepositStatus.PENDING, row.getStatus());
        assertEquals("Recarga inicial", row.getDescription());
        assertEquals("evt-1", row.getLastEventId());
        assertEquals(occurredAt, row.getCreatedAt());
        assertEquals(occurredAt, row.getUpdatedAt());
        assertEquals(1, row.getProjectionVersion());
    }

    @Test
    void initiatedEvent_replayed_doesNotCreateASecondRow() {
        when(repository.existsByDepositId(DepositId.of(depositId))).thenReturn(true);

        projection.on(
                new DepositInitiatedEvent(depositId, accountId, 12500L, "PEN", "STRIPE", "idem-1", "desc"),
                "evt-1", Instant.now());

        verify(repository, never()).save(any());
    }

    @Test
    void fullHappyPath_appliesEachTransitionInOrder() {
        DepositViewEntity row = initiatedRow();
        when(repository.findByDepositId(DepositId.of(depositId))).thenReturn(Optional.of(row));

        projection.on(new DepositProviderReferenceRegisteredEvent(depositId, "STRIPE", "cs_123", "https://checkout/cs_123"),
                "evt-2", Instant.now());
        assertEquals(DepositStatus.PENDING, row.getStatus(), "registering the reference must not change status");
        assertEquals("cs_123", row.getProviderDepositId().value());
        assertEquals("https://checkout/cs_123", row.getActionUrl());
        assertEquals(2, row.getProjectionVersion());

        projection.on(new DepositProcessingStartedEvent(depositId, accountId, 12500L, "PEN", "STRIPE"),
                "evt-3", Instant.now());
        assertEquals(DepositStatus.PROCESSING, row.getStatus());
        assertEquals(3, row.getProjectionVersion());

        projection.on(new DepositSucceededEvent(depositId, accountId, 12500L, "PEN", "STRIPE"),
                "evt-4", Instant.now());
        assertEquals(DepositStatus.SUCCEEDED, row.getStatus());
        assertEquals(4, row.getProjectionVersion());
        assertEquals(12500L, row.getAmountMinor(), "amount must survive every intermediate transition unchanged");

        verify(repository, org.mockito.Mockito.times(3)).save(row);
    }

    @Test
    void failedEvent_setsStatusAndFailureReason() {
        DepositViewEntity row = initiatedRow();
        when(repository.findByDepositId(DepositId.of(depositId))).thenReturn(Optional.of(row));

        projection.on(new DepositFailedEvent(depositId, accountId, 12500L, "PEN", "STRIPE", "DECLINED"),
                "evt-2", Instant.now());

        assertEquals(DepositStatus.FAILED, row.getStatus());
        assertEquals(FailureReason.DECLINED, row.getFailureReason());
    }

    @Test
    void cancelledEvent_setsStatusAndCancellationReason() {
        DepositViewEntity row = initiatedRow();
        when(repository.findByDepositId(DepositId.of(depositId))).thenReturn(Optional.of(row));

        projection.on(new DepositCancelledEvent(depositId, accountId, 12500L, "PEN", "STRIPE", "EXPIRED"),
                "evt-2", Instant.now());

        assertEquals(DepositStatus.CANCELLED, row.getStatus());
        assertEquals("EXPIRED", row.getCancellationReason());
    }

    @Test
    void duplicateEvent_isIgnored() {
        DepositViewEntity row = initiatedRow(); // lastEventId = "evt-1"
        when(repository.findByDepositId(DepositId.of(depositId))).thenReturn(Optional.of(row));

        projection.on(new DepositActionRequiredEvent(depositId, accountId, 12500L, "PEN", "STRIPE"),
                "evt-1", Instant.now());

        assertEquals(DepositStatus.PENDING, row.getStatus(), "a re-applied event must not mutate the row");
        assertEquals(1, row.getProjectionVersion());
        verify(repository, never()).save(any());
    }

    @Test
    void getDepositById_present_mapsToSummary() {
        DepositViewEntity row = initiatedRow();
        when(repository.findByDepositId(DepositId.of(depositId))).thenReturn(Optional.of(row));

        Optional<DepositSummary> result = projection.getDepositById(new GetDepositByIdQuery(DepositId.of(depositId)));

        assertTrue(result.isPresent());
        DepositSummary summary = result.get();
        assertEquals(DepositId.of(depositId), summary.depositId());
        assertEquals(12500L, summary.amount().amountMinor());
        assertEquals(row.getCurrency(), summary.amount().currency());
        assertEquals(DepositStatus.PENDING, summary.status());
    }

    @Test
    void getDepositById_absent_returnsEmpty() {
        when(repository.findByDepositId(DepositId.of(depositId))).thenReturn(Optional.empty());

        assertFalse(projection.getDepositById(new GetDepositByIdQuery(DepositId.of(depositId))).isPresent());
    }

    @Test
    void listDepositsByAccount_withoutStatusFilter_usesUnfilteredQuery() {
        DepositViewEntity row = initiatedRow();
        Pageable pageable = PageRequest.of(0, 20);
        Page<DepositViewEntity> page = new PageImpl<>(List.of(row), pageable, 1);
        when(repository.findByAccountIdOrderByCreatedAtDesc(eq(AccountId.of(accountId)), any()))
                .thenReturn(page);

        DepositSummaryPage result = projection.listDepositsByAccount(
                new ListDepositsByAccountQuery(AccountId.of(accountId), null, 0, 20));

        assertEquals(1, result.items().size());
        assertEquals(1L, result.totalElements());
        verify(repository, never()).findByAccountIdAndStatusOrderByCreatedAtDesc(any(), any(), any());
    }

    @Test
    void listDepositsByAccount_withStatusFilter_usesFilteredQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<DepositViewEntity> page = new PageImpl<>(List.of(), pageable, 0);
        when(repository.findByAccountIdAndStatusOrderByCreatedAtDesc(
                eq(AccountId.of(accountId)), eq(DepositStatus.SUCCEEDED), any()))
                .thenReturn(page);

        DepositSummaryPage result = projection.listDepositsByAccount(
                new ListDepositsByAccountQuery(AccountId.of(accountId), DepositStatus.SUCCEEDED, 0, 20));

        assertTrue(result.items().isEmpty());
        verify(repository, never()).findByAccountIdOrderByCreatedAtDesc(any(), any());
    }

    private DepositViewEntity initiatedRow() {
        return DepositViewEntity.initiate(
                DepositId.of(depositId),
                AccountId.of(accountId),
                12500L,
                Currency.PEN,
                Provider.STRIPE,
                "desc",
                "evt-1",
                Instant.now());
    }
}
