package com.liquilabs.vankoo.finance.application.internal.commandservices;

import com.liquilabs.vankoo.finance.domain.model.commands.CreditWalletCommand;
import com.liquilabs.vankoo.finance.domain.model.commands.OpenWalletCommand;
import com.liquilabs.vankoo.finance.domain.model.events.DepositSucceededEvent;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories.WalletCreditedDepositRepository;
import com.fasterxml.uuid.Generators;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Reacts to {@code DepositSucceededEvent} by crediting the investor's wallet
 * for that currency — opening it first if this is the first deposit ever
 * made in that currency.
 *
 * <p><strong>Temporary, on purpose:</strong> this is the lazy-creation path
 * documented in {@code wallet-contracts.md}. It will be replaced once
 * Anjali's Kafka work lands, by opening the wallet from Profile's
 * account-created event instead of from the first deposit.
 *
 * <p>A plain {@code @EventHandler}, not a saga: opening (when needed) and
 * crediting are two sequential dispatches with nothing to wait for and
 * nothing to compensate.
 */
@Component
@ProcessingGroup("wallet-crediting")
public class WalletCreditor {

    private static final Logger LOGGER = LoggerFactory.getLogger(WalletCreditor.class);

    private final EventStore eventStore;
    private final WalletCreditedDepositRepository dedupRepository;
    private final CommandGateway commandGateway;

    public WalletCreditor(EventStore eventStore,
                          WalletCreditedDepositRepository dedupRepository,
                          CommandGateway commandGateway) {
        this.eventStore = eventStore;
        this.dedupRepository = dedupRepository;
        this.commandGateway = commandGateway;
    }

    @EventHandler
    public void on(DepositSucceededEvent event) {
        AccountId accountId = AccountId.of(event.accountId());
        Currency currency = Currency.valueOf(event.currency());
        WalletId walletId = WalletId.derive(accountId, currency);
        DepositId depositId = DepositId.of(event.depositId());

        // Reads the event store directly, not a projection: ADR-0001 forbids
        // deciding admission against a Read Model.
        if (eventStore.lastSequenceNumberFor(walletId.toString()).isEmpty()) {
            commandGateway.sendAndWait(new OpenWalletCommand(walletId, accountId, currency));
        }

        int inserted = dedupRepository.insertIfAbsent(
                Generators.timeBasedEpochGenerator().generate(),
                walletId.value(),
                depositId.value(),
                Instant.now());
        if (inserted == 0) {
            LOGGER.debug("Deposit already credited to wallet, skipping: walletId={}, depositId={}",
                    walletId, depositId);
            return;
        }

        commandGateway.sendAndWait(new CreditWalletCommand(
                walletId, new Money(event.amountMinor(), currency), depositId));
    }
}
