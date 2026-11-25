package com.liquilabs.vankoo.finance.application.internal.commandservices;

import com.liquilabs.vankoo.finance.domain.model.commands.CreditWalletFromAuctionCommand;
import com.liquilabs.vankoo.finance.domain.model.commands.OpenWalletCommand;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AuctionId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories.WalletCreditedAuctionRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Plain unit test, same shape as {@code WalletCreditorTest}. */
@ExtendWith(MockitoExtension.class)
class AuctionFundingCreditServiceImplTest {

    @Mock
    private EventStore eventStore;

    @Mock
    private WalletCreditedAuctionRepository dedupRepository;

    @Mock
    private CommandGateway commandGateway;

    private AuctionFundingCreditServiceImpl service;

    private AuctionId auctionId;
    private AccountId mypeAccountId;
    private WalletId walletId;
    private final Money advance = new Money(980_000L, Currency.PEN);

    @BeforeEach
    void setUp() {
        service = new AuctionFundingCreditServiceImpl(eventStore, dedupRepository, commandGateway);
        auctionId = AuctionId.of(UUID.randomUUID().toString());
        mypeAccountId = AccountId.of(UUID.randomUUID().toString());
        walletId = WalletId.derive(mypeAccountId, Currency.PEN);
    }

    @Test
    void firstFundedAuction_opensTheMypeWalletThenCredits() {
        when(dedupRepository.insertIfAbsent(any(), eq(walletId.value()), eq(auctionId.value()), any())).thenReturn(1);
        when(eventStore.lastSequenceNumberFor(walletId.toString())).thenReturn(Optional.empty());

        assertTrue(service.creditAdvance(auctionId, mypeAccountId, advance));

        ArgumentCaptor<Object> dispatched = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, times(2)).sendAndWait(dispatched.capture());
        assertEquals(new OpenWalletCommand(walletId, mypeAccountId, Currency.PEN), dispatched.getAllValues().get(0));
        assertEquals(new CreditWalletFromAuctionCommand(walletId, advance, auctionId), dispatched.getAllValues().get(1));
    }

    @Test
    void walletAlreadyOpen_onlyCredits() {
        when(dedupRepository.insertIfAbsent(any(), any(), any(), any())).thenReturn(1);
        when(eventStore.lastSequenceNumberFor(walletId.toString())).thenReturn(Optional.of(3L));

        assertTrue(service.creditAdvance(auctionId, mypeAccountId, advance));

        verify(commandGateway).sendAndWait(new CreditWalletFromAuctionCommand(walletId, advance, auctionId));
        verify(commandGateway, never()).sendAndWait(any(OpenWalletCommand.class));
    }

    @Test
    void auctionAlreadyCredited_dispatchesNothing() {
        when(dedupRepository.insertIfAbsent(any(), any(), any(), any())).thenReturn(0);

        assertFalse(service.creditAdvance(auctionId, mypeAccountId, advance));

        verifyNoInteractions(commandGateway);
        verifyNoInteractions(eventStore);
    }
}
