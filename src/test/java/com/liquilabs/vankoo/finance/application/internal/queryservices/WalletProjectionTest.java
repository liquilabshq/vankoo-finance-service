package com.liquilabs.vankoo.finance.application.internal.queryservices;

import com.liquilabs.vankoo.finance.domain.model.events.WalletCreditedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.WalletDebitedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.WalletOpenedEvent;
import com.liquilabs.vankoo.finance.domain.model.queries.GetWalletBalanceQuery;
import com.liquilabs.vankoo.finance.domain.model.queries.ListWalletMovementsQuery;
import com.liquilabs.vankoo.finance.domain.model.queries.WalletBalance;
import com.liquilabs.vankoo.finance.domain.model.queries.WalletMovementPage;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.MovementDirection;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletMovementKind;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletMovementType;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.WalletMovementEntity;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.WalletViewEntity;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories.WalletMovementRepository;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories.WalletViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit test, no Spring context: the {@code @EventHandler} methods are
 * called directly as regular Java methods, and both repositories are mocked.
 */
@ExtendWith(MockitoExtension.class)
class WalletProjectionTest {

    @Mock
    private WalletViewRepository walletViewRepository;

    @Mock
    private WalletMovementRepository walletMovementRepository;

    private WalletProjection projection;

    private String walletId;
    private String accountId;
    private String depositId;

    @BeforeEach
    void setUp() {
        projection = new WalletProjection(walletViewRepository, walletMovementRepository);
        walletId = UUID.randomUUID().toString();
        accountId = UUID.randomUUID().toString();
        depositId = UUID.randomUUID().toString();
    }

    @Test
    void openedEvent_createsBalanceRow() {
        when(walletViewRepository.existsByWalletId(WalletId.of(walletId))).thenReturn(false);
        Instant occurredAt = Instant.now();

        projection.on(new WalletOpenedEvent(walletId, accountId, "PEN"), "evt-1", occurredAt);

        ArgumentCaptor<WalletViewEntity> saved = ArgumentCaptor.forClass(WalletViewEntity.class);
        verify(walletViewRepository).save(saved.capture());
        WalletViewEntity row = saved.getValue();

        assertEquals(WalletId.of(walletId), row.getWalletId());
        assertEquals(AccountId.of(accountId), row.getAccountId());
        assertEquals(Currency.PEN, row.getCurrency());
        assertEquals(0L, row.getBalanceMinor());
        assertEquals("evt-1", row.getLastEventId());
    }

    @Test
    void openedEvent_replayed_doesNotCreateASecondRow() {
        when(walletViewRepository.existsByWalletId(WalletId.of(walletId))).thenReturn(true);

        projection.on(new WalletOpenedEvent(walletId, accountId, "PEN"), "evt-1", Instant.now());

        verify(walletViewRepository, never()).save(any());
    }

    @Test
    void creditedEvent_updatesBalanceAndInsertsRecargaMovement() {
        WalletViewEntity row = openedRow();
        when(walletViewRepository.findByWalletId(WalletId.of(walletId))).thenReturn(Optional.of(row));
        Instant occurredAt = Instant.now();

        projection.on(new WalletCreditedEvent(walletId, 12_500L, "PEN", depositId), "evt-2", occurredAt);

        assertEquals(12_500L, row.getBalanceMinor());
        verify(walletViewRepository).save(row);
        verify(walletMovementRepository).insertIfAbsent(
                any(), eq("evt-2"), eq(UUID.fromString(walletId)), eq(UUID.fromString(accountId)),
                eq("PEN"), eq(12_500L), eq("RECARGA"), eq("CREDIT"),
                eq(UUID.fromString(depositId)), eq(occurredAt));
    }

    @Test
    void debitedEvent_updatesBalanceAndInsertsMovementWithoutSourceDeposit() {
        WalletViewEntity row = openedRow();
        row.credit(50_000L, "evt-2", Instant.now()); // enough balance to debit from
        when(walletViewRepository.findByWalletId(WalletId.of(walletId))).thenReturn(Optional.of(row));
        Instant occurredAt = Instant.now();

        projection.on(new WalletDebitedEvent(walletId, 10_000L, "PEN", WalletMovementType.RETIRO.name()),
                "evt-3", occurredAt);

        assertEquals(40_000L, row.getBalanceMinor());
        verify(walletMovementRepository).insertIfAbsent(
                any(), eq("evt-3"), eq(UUID.fromString(walletId)), eq(UUID.fromString(accountId)),
                eq("PEN"), eq(10_000L), eq("RETIRO"), eq("DEBIT"),
                isNull(), eq(occurredAt));
    }

    @Test
    void creditedEvent_replayed_doesNotDoubleApplyBalanceOrInsertMovement() {
        WalletViewEntity row = openedRow(); // lastEventId = "evt-1"
        when(walletViewRepository.findByWalletId(WalletId.of(walletId))).thenReturn(Optional.of(row));

        projection.on(new WalletCreditedEvent(walletId, 12_500L, "PEN", depositId), "evt-1", Instant.now());

        assertEquals(0L, row.getBalanceMinor(), "a re-applied event must not mutate the row");
        verify(walletViewRepository, never()).save(any());
        verify(walletMovementRepository, never())
                .insertIfAbsent(any(), any(), any(), any(), any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void getWalletBalance_present_mapsToWalletBalance() {
        WalletViewEntity row = openedRow();
        when(walletViewRepository.findByWalletId(WalletId.derive(AccountId.of(accountId), Currency.PEN)))
                .thenReturn(Optional.of(row));

        Optional<WalletBalance> result = projection.getWalletBalance(
                new GetWalletBalanceQuery(AccountId.of(accountId), Currency.PEN));

        assertTrue(result.isPresent());
        assertEquals(0L, result.get().balance().amountMinor());
        assertEquals(Currency.PEN, result.get().balance().currency());
    }

    @Test
    void getWalletBalance_absent_returnsEmpty() {
        when(walletViewRepository.findByWalletId(any())).thenReturn(Optional.empty());

        Optional<WalletBalance> result = projection.getWalletBalance(
                new GetWalletBalanceQuery(AccountId.of(accountId), Currency.PEN));

        assertFalse(result.isPresent());
    }

    @Test
    void listWalletMovements_returnsPagedResults() {
        WalletMovementEntity movement = movementEntity();
        WalletId derivedWalletId = WalletId.derive(AccountId.of(accountId), Currency.PEN);
        Page<WalletMovementEntity> page = new PageImpl<>(List.of(movement), PageRequest.of(0, 20), 1);
        when(walletMovementRepository.findByWalletIdOrderByOccurredAtDesc(eq(derivedWalletId), any()))
                .thenReturn(page);

        WalletMovementPage result = projection.listWalletMovements(
                new ListWalletMovementsQuery(AccountId.of(accountId), Currency.PEN, 0, 20));

        assertEquals(1, result.items().size());
        assertEquals(1L, result.totalElements());
        assertNull(result.items().get(0).sourceDepositId(), "a RETIRO row carries no sourceDepositId");
    }

    @Test
    void listWalletMovements_walletNeverOpened_returnsEmptyPageNotError() {
        when(walletMovementRepository.findByWalletIdOrderByOccurredAtDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        WalletMovementPage result = projection.listWalletMovements(
                new ListWalletMovementsQuery(AccountId.of(accountId), Currency.PEN, 0, 20));

        assertTrue(result.items().isEmpty());
        assertEquals(0L, result.totalElements());
    }

    private WalletViewEntity openedRow() {
        return WalletViewEntity.open(
                WalletId.of(walletId), AccountId.of(accountId), Currency.PEN, "evt-1", Instant.now());
    }

    private WalletMovementEntity movementEntity() {
        WalletMovementEntity entity = mock(WalletMovementEntity.class);
        when(entity.getType()).thenReturn(WalletMovementKind.RETIRO);
        when(entity.getDirection()).thenReturn(MovementDirection.DEBIT);
        when(entity.getAmountMinor()).thenReturn(10_000L);
        when(entity.getCurrency()).thenReturn(Currency.PEN);
        when(entity.getSourceDepositId()).thenReturn(null);
        when(entity.getOccurredAt()).thenReturn(Instant.now());
        return entity;
    }
}
