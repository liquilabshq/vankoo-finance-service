package com.liquilabs.vankoo.finance.application.internal.commandservices;

import com.liquilabs.vankoo.finance.domain.model.commands.CreditWalletFromAuctionCommand;
import com.liquilabs.vankoo.finance.domain.model.commands.OpenWalletCommand;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AuctionId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.domain.services.AuctionFundingCreditService;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories.WalletCreditedAuctionRepository;
import com.fasterxml.uuid.Generators;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Implements {@link AuctionFundingCreditService} with the same three steps as
 * {@code WalletCreditor} does for a deposit: open the wallet if it does not
 * exist yet, pass the deduplication barrier, credit.
 *
 * <p>A MYPE never deposits, so its first funded auction is what opens its
 * wallet. The open is checked against the event store, never a projection
 * (ADR-0001), exactly like the deposit path.
 *
 * <p>{@code @Transactional} spans both dispatches on purpose: if the credit is
 * refused or Axon Server is down, the barrier row rolls back with it and the
 * Kafka redelivery tries again, instead of leaving an auction marked as paid
 * that never was. The residual window — Axon Server committed the credit and
 * then the database commit failed — would credit twice on redelivery; it is
 * the same window {@code WalletCreditor} lives with.
 */
@Service
public class AuctionFundingCreditServiceImpl implements AuctionFundingCreditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionFundingCreditServiceImpl.class);

    private final EventStore eventStore;
    private final WalletCreditedAuctionRepository dedupRepository;
    private final CommandGateway commandGateway;

    public AuctionFundingCreditServiceImpl(EventStore eventStore,
                                           WalletCreditedAuctionRepository dedupRepository,
                                           CommandGateway commandGateway) {
        this.eventStore = eventStore;
        this.dedupRepository = dedupRepository;
        this.commandGateway = commandGateway;
    }

    @Override
    @Transactional
    public boolean creditAdvance(AuctionId auctionId, AccountId mypeAccountId, Money advance) {
        WalletId walletId = WalletId.derive(mypeAccountId, advance.currency());

        int inserted = dedupRepository.insertIfAbsent(
                Generators.timeBasedEpochGenerator().generate(),
                walletId.value(),
                auctionId.value(),
                Instant.now());
        if (inserted == 0) {
            LOGGER.debug("Auction advance already credited, skipping: auctionId={}, walletId={}",
                    auctionId, walletId);
            return false;
        }

        if (eventStore.lastSequenceNumberFor(walletId.toString()).isEmpty()) {
            commandGateway.sendAndWait(new OpenWalletCommand(walletId, mypeAccountId, advance.currency()));
        }
        commandGateway.sendAndWait(new CreditWalletFromAuctionCommand(walletId, advance, auctionId));
        LOGGER.info("Credited auction advance: auctionId={}, walletId={}, amountMinor={}",
                auctionId, walletId, advance.amountMinor());
        return true;
    }
}
