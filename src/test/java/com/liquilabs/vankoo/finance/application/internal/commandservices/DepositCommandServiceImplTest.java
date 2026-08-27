package com.liquilabs.vankoo.finance.application.internal.commandservices;

import com.liquilabs.vankoo.finance.domain.exceptions.IdempotencyKeyConflictException;
import com.liquilabs.vankoo.finance.domain.exceptions.InvalidDepositAmountException;
import com.liquilabs.vankoo.finance.domain.model.commands.InitiateDepositCommand;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.IdempotencyKey;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.DepositCommandIdempotency;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories.DepositCommandIdempotencyRepository;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit test, no Spring context: {@code handle(...)} is called directly
 * as a regular Java method, and {@link DepositCommandIdempotencyRepository}
 * and {@link CommandGateway} are mocked.
 */
@ExtendWith(MockitoExtension.class)
class DepositCommandServiceImplTest {

    @Mock
    private DepositCommandIdempotencyRepository idempotencyRepository;

    @Mock
    private CommandGateway commandGateway;

    private DepositCommandServiceImpl service;

    private InitiateDepositCommand command;

    @BeforeEach
    void setUp() {
        service = new DepositCommandServiceImpl(idempotencyRepository, commandGateway);
        command = new InitiateDepositCommand(
                new DepositId(),
                AccountId.of(UUID.randomUUID().toString()),
                new Money(12_500L, Currency.PEN),
                Provider.STRIPE,
                new IdempotencyKey("client-key-1"),
                Instant.now(),
                "Recarga de saldo");
    }

    @Test
    void newKey_dispatchesAndReturnsTheMintedDepositId() {
        when(idempotencyRepository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(1);

        DepositId result = service.handle(command);

        assertEquals(command.depositId(), result);
        verify(commandGateway).sendAndWait(command);
    }

    @Test
    void sameKeySameContent_returnsExistingDepositIdWithoutDispatching() {
        when(idempotencyRepository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(0);

        DepositCommandIdempotency existing = existingRowFor(sameContentHash());
        DepositId existingDepositId = new DepositId();
        when(existing.getDepositId()).thenReturn(existingDepositId);
        when(idempotencyRepository.findByAccountIdAndIdempotencyKey(
                eq(command.accountId()), eq(command.idempotencyKey().value())))
                .thenReturn(Optional.of(existing));

        DepositId result = service.handle(command);

        assertEquals(existingDepositId, result);
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    void sameKeyDifferentContent_throwsConflictWithoutDispatching() {
        when(idempotencyRepository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(0);

        DepositCommandIdempotency existing = existingRowFor("a-completely-different-hash");
        when(idempotencyRepository.findByAccountIdAndIdempotencyKey(
                eq(command.accountId()), eq(command.idempotencyKey().value())))
                .thenReturn(Optional.of(existing));

        assertThrows(IdempotencyKeyConflictException.class, () -> service.handle(command));
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    void rejectedCommand_propagatesTheException() {
        when(idempotencyRepository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(1);
        doThrow(new InvalidDepositAmountException(-1)).when(commandGateway).sendAndWait(command);

        assertThrows(InvalidDepositAmountException.class, () -> service.handle(command));
    }

    /** Same canonicalization as {@code DepositCommandServiceImpl}: recomputed here to avoid a shared secret hash. */
    private String sameContentHash() {
        String canonical = command.accountId().value() + "|"
                + command.amount().amountMinor() + "|"
                + command.amount().currency() + "|"
                + command.provider() + "|"
                + command.description();
        return RequestDigest.sha256Hex(canonical);
    }

    private static DepositCommandIdempotency existingRowFor(String requestHash) {
        DepositCommandIdempotency row = mock(DepositCommandIdempotency.class);
        when(row.getRequestHash()).thenReturn(requestHash);
        return row;
    }
}
