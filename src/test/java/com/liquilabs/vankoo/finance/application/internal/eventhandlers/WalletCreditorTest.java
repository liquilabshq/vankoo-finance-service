package com.liquilabs.vankoo.finance.application.internal.eventhandlers;

import com.liquilabs.vankoo.finance.domain.model.commands.CreditWalletCommand;
import com.liquilabs.vankoo.finance.domain.model.commands.OpenWalletCommand;
import com.liquilabs.vankoo.finance.domain.model.events.DepositSucceededEvent;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories.WalletCreditedDepositRepository;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit test, no Spring context: {@code on(...)} is called directly as a
 * regular Java method, and {@link EventStore}, {@link WalletCreditedDepositRepository}
 * and {@link CommandGateway} are mocked.
 */
@ExtendWith(MockitoExtension.class)
class WalletCreditorTest {

    @Mock
    private EventStore eventStore;

    @Mock
    private WalletCreditedDepositRepository dedupRepository;

    @Mock
    private CommandGateway commandGateway;

    private WalletCreditor creditor;

    private String accountId;
    private String depositId;
    private WalletId walletId;

    @BeforeEach
    void setUp() {
        creditor = new WalletCreditor(eventStore, dedupRepository, commandGateway);
        accountId = UUID.randomUUID().toString();
        depositId = UUID.randomUUID().toString();
        walletId = WalletId.derive(AccountId.of(accountId), Currency.PEN);
    }

    @Test
    void firstDepositInACurrency_opensThenCredits() {
        when(eventStore.lastSequenceNumberFor(walletId.toString())).thenReturn(Optional.empty());
        when(dedupRepository.insertIfAbsent(any(), any(), any(), any())).thenReturn(1);

        creditor.on(new DepositSucceededEvent(depositId, accountId, 12_500L, "PEN", "STRIPE"));

        ArgumentCaptor<Object> dispatched = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, times(2)).sendAndWait(dispatched.capture());

        assertEquals(OpenWalletCommand.class, dispatched.getAllValues().get(0).getClass());
        assertEquals(CreditWalletCommand.class, dispatched.getAllValues().get(1).getClass());
    }

    @Test
    void subsequentDepositInAnAlreadyOpenWallet_onlyCredits() {
        when(eventStore.lastSequenceNumberFor(walletId.toString())).thenReturn(Optional.of(0L));
        when(dedupRepository.insertIfAbsent(any(), any(), any(), any())).thenReturn(1);

        creditor.on(new DepositSucceededEvent(depositId, accountId, 12_500L, "PEN", "STRIPE"));

        verify(commandGateway, never()).sendAndWait(any(OpenWalletCommand.class));
        verify(commandGateway).sendAndWait(any(CreditWalletCommand.class));
    }

    @Test
    void depositAlreadyCredited_doesNotDispatchCreditAgain() {
        when(eventStore.lastSequenceNumberFor(walletId.toString())).thenReturn(Optional.of(0L));
        when(dedupRepository.insertIfAbsent(any(), any(), any(), any())).thenReturn(0);

        creditor.on(new DepositSucceededEvent(depositId, accountId, 12_500L, "PEN", "STRIPE"));

        verify(commandGateway, never()).sendAndWait(any());
    }
}
