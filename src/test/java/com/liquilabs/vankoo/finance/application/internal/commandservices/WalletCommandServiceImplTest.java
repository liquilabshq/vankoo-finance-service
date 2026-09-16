package com.liquilabs.vankoo.finance.application.internal.commandservices;

import com.liquilabs.vankoo.finance.domain.exceptions.CommandRejectedException;
import com.liquilabs.vankoo.finance.domain.exceptions.IdempotencyKeyConflictException;
import com.liquilabs.vankoo.finance.domain.model.commands.DebitWalletCommand;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DebitId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.IdempotencyKey;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletMovementType;
import com.liquilabs.vankoo.finance.infrastructure.eventstore.axon.CommandRejection;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.WalletDebitCommandIdempotency;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories.WalletDebitCommandIdempotencyRepository;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit test, no Spring context, same shape as
 * {@link DepositCommandServiceImplTest}: {@code handle(...)} is called
 * directly, the repository and the {@link CommandGateway} are mocked.
 */
@ExtendWith(MockitoExtension.class)
class WalletCommandServiceImplTest {

    @Mock
    private WalletDebitCommandIdempotencyRepository idempotencyRepository;

    @Mock
    private CommandGateway commandGateway;

    private WalletCommandServiceImpl service;

    private AccountId accountId;
    private IdempotencyKey idempotencyKey;
    private DebitWalletCommand command;

    @BeforeEach
    void setUp() {
        service = new WalletCommandServiceImpl(idempotencyRepository, commandGateway);
        accountId = AccountId.of(UUID.randomUUID().toString());
        idempotencyKey = new IdempotencyKey("client-key-1");
        command = new DebitWalletCommand(
                WalletId.derive(accountId, Currency.PEN),
                new DebitId(),
                new Money(5_000L, Currency.PEN),
                WalletMovementType.INVERSION);
    }

    @Test
    void newKey_dispatchesAndReturnsTheMintedDebitId() {
        when(idempotencyRepository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(1);

        DebitId result = service.handle(command, accountId, idempotencyKey);

        assertEquals(command.debitId(), result);
        verify(idempotencyRepository).insertIfAbsent(
                any(), eq(accountId.value()), eq("client-key-1"), eq(command.debitId().value()), any(), any());
        verify(commandGateway).sendAndWait(command);
    }

    @Test
    void sameKeySameContent_returnsExistingDebitIdWithoutDispatching() {
        when(idempotencyRepository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(0);

        WalletDebitCommandIdempotency existing = existingRowFor(sameContentHash());
        DebitId existingDebitId = new DebitId();
        when(existing.getDebitId()).thenReturn(existingDebitId);
        when(idempotencyRepository.findByAccountIdAndIdempotencyKey(eq(accountId), eq("client-key-1")))
                .thenReturn(Optional.of(existing));

        DebitId result = service.handle(command, accountId, idempotencyKey);

        assertEquals(existingDebitId, result);
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    void sameKeyDifferentContent_throwsConflictWithoutDispatching() {
        when(idempotencyRepository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(0);

        WalletDebitCommandIdempotency existing = existingRowFor("a-completely-different-hash");
        when(idempotencyRepository.findByAccountIdAndIdempotencyKey(eq(accountId), eq("client-key-1")))
                .thenReturn(Optional.of(existing));

        assertThrows(IdempotencyKeyConflictException.class, () -> service.handle(command, accountId, idempotencyKey));
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    void remoteRejectionWithDetails_isTranslatedToCommandRejectedException() {
        when(idempotencyRepository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(1);
        // What AxonServerCommandBus actually hands back when the aggregate threw
        // InsufficientBalanceException and the handler interceptor attached details.
        doThrow(new CommandExecutionException("Wallet x has balance 100", new RuntimeException("remote"),
                new CommandRejection(CommandRejection.INSUFFICIENT_BALANCE, "Wallet x has balance 100")))
                .when(commandGateway).sendAndWait(command);

        CommandRejectedException rejected = assertThrows(CommandRejectedException.class,
                () -> service.handle(command, accountId, idempotencyKey));

        assertEquals("insufficient-balance", rejected.getCode());
        assertEquals("Wallet x has balance 100", rejected.getMessage());
    }

    @Test
    void remoteFailureWithoutDetails_propagatesUntouched() {
        when(idempotencyRepository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(1);
        CommandExecutionException unknown = new CommandExecutionException("boom", new RuntimeException("remote"));
        doThrow(unknown).when(commandGateway).sendAndWait(command);

        CommandExecutionException thrown = assertThrows(CommandExecutionException.class,
                () -> service.handle(command, accountId, idempotencyKey));

        assertSame(unknown, thrown);
    }

    /** Same canonicalization as {@code WalletCommandServiceImpl}: recomputed here to avoid a shared secret hash. */
    private String sameContentHash() {
        String canonical = accountId.value() + "|"
                + command.amount().currency() + "|"
                + command.amount().amountMinor() + "|"
                + command.reason();
        return RequestDigest.sha256Hex(canonical);
    }

    private static WalletDebitCommandIdempotency existingRowFor(String requestHash) {
        WalletDebitCommandIdempotency row = mock(WalletDebitCommandIdempotency.class);
        when(row.getRequestHash()).thenReturn(requestHash);
        return row;
    }
}
