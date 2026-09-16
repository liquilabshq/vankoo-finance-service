package com.liquilabs.vankoo.finance.domain.model.aggregates;

import com.liquilabs.vankoo.finance.domain.exceptions.InsufficientBalanceException;
import com.liquilabs.vankoo.finance.domain.exceptions.InvalidCreditAmountException;
import com.liquilabs.vankoo.finance.domain.exceptions.InvalidDebitAmountException;
import com.liquilabs.vankoo.finance.domain.model.commands.CreditWalletCommand;
import com.liquilabs.vankoo.finance.domain.model.commands.DebitWalletCommand;
import com.liquilabs.vankoo.finance.domain.model.commands.OpenWalletCommand;
import com.liquilabs.vankoo.finance.domain.model.events.WalletCreditedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.WalletDebitedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.WalletOpenedEvent;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * Holds an investor's balance in one currency. One {@code Wallet} exists per
 * {@code (accountId, currency)} — see {@code wallet-contracts.md}.
 *
 * <p>Follows {@code Deposit}'s exact idiom: invariants are checked in the
 * {@code @CommandHandler}s, before {@code apply(...)}; {@code @EventSourcingHandler}s
 * only assign state, never validate.
 */
@Aggregate(snapshotTriggerDefinition = "walletSnapshotTriggerDefinition")
public class Wallet {

    @AggregateIdentifier
    private String walletId;
    private String accountId;
    private Currency currency;
    private long balanceMinor;

    protected Wallet() {
    }

    @CommandHandler
    public Wallet(OpenWalletCommand command) {
        apply(new WalletOpenedEvent(
                command.walletId().toString(),
                command.accountId().toString(),
                command.currency().name()));
    }

    @CommandHandler
    public void handle(CreditWalletCommand command) {
        if (command.amount().amountMinor() <= 0) {
            throw new InvalidCreditAmountException(command.amount().amountMinor());
        }
        apply(new WalletCreditedEvent(
                walletId,
                command.amount().amountMinor(),
                command.amount().currency().name(),
                command.sourceDepositId().toString()));
    }

    @CommandHandler
    public void handle(DebitWalletCommand command) {
        if (command.amount().amountMinor() <= 0) {
            throw new InvalidDebitAmountException(command.amount().amountMinor());
        }
        if (command.amount().amountMinor() > balanceMinor) {
            throw new InsufficientBalanceException(walletId, balanceMinor, command.amount().amountMinor());
        }
        apply(new WalletDebitedEvent(
                walletId,
                command.amount().amountMinor(),
                command.amount().currency().name(),
                command.reason().name(),
                command.debitId().toString()));
    }

    @EventSourcingHandler
    public void on(WalletOpenedEvent event) {
        this.walletId = event.walletId();
        this.accountId = event.accountId();
        this.currency = Currency.valueOf(event.currency());
        this.balanceMinor = 0L;
    }

    @EventSourcingHandler
    public void on(WalletCreditedEvent event) {
        this.balanceMinor += event.amountMinor();
    }

    @EventSourcingHandler
    public void on(WalletDebitedEvent event) {
        this.balanceMinor -= event.amountMinor();
    }
}
