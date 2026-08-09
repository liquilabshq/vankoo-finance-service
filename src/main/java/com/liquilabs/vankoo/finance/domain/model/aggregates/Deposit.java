package com.liquilabs.vankoo.finance.domain.model.aggregates;

import com.liquilabs.vankoo.finance.domain.model.commands.ApplyProviderDepositUpdateCommand;
import com.liquilabs.vankoo.finance.domain.model.commands.InitiateDepositCommand;
import com.liquilabs.vankoo.finance.domain.model.commands.RegisterDepositProviderReferenceCommand;
import com.liquilabs.vankoo.finance.domain.model.events.DepositActionRequiredEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositCancelledEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositFailedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositInitiatedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositProcessingStartedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositProviderReferenceRegisteredEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositSucceededEvent;
import com.liquilabs.vankoo.finance.domain.model.exceptions.InvalidDepositAmountException;
import com.liquilabs.vankoo.finance.domain.model.exceptions.ProviderReferenceMismatchException;
import com.liquilabs.vankoo.finance.domain.model.exceptions.TerminalStateTransitionException;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositStatus;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.FailureReason;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * Models one thing only: an investor topping up their wallet. It stops at
 * {@link DepositSucceededEvent} — it does not credit any balance, that is the
 * future {@code Wallet} aggregate's job.
 *
 * <p>Follows the course guide's Axon 4 model literally (see ADR-0002):
 * invariants are checked in the {@code @CommandHandler}s, before {@code apply(...)};
 * {@code @EventSourcingHandler}s only assign state, never validate.
 */
@Aggregate
public class Deposit {

    @AggregateIdentifier
    private String depositId;
    private String accountId;
    private long amountMinor;
    private Currency currency;
    private Provider provider;
    private String providerDepositId;
    private DepositStatus status;
    private FailureReason failureReason;

    protected Deposit() {
    }

    @CommandHandler
    public Deposit(InitiateDepositCommand command) {
        if (command.amount().amountMinor() <= 0) {
            throw new InvalidDepositAmountException(command.amount().amountMinor());
        }
        apply(new DepositInitiatedEvent(
                command.depositId().toString(),
                command.accountId().toString(),
                command.amount().amountMinor(),
                command.amount().currency().name(),
                command.provider().name(),
                command.idempotencyKey().value(),
                command.description()));
    }

    @CommandHandler
    public void handle(RegisterDepositProviderReferenceCommand command) {
        if (providerDepositId != null) {
            boolean sameReference = providerDepositId.equals(command.providerDepositId().value())
                    && provider == command.provider();
            if (sameReference) {
                return; // rule 4: exact repetition is idempotent
            }
            throw new ProviderReferenceMismatchException(
                    "Deposit " + depositId + " already has a provider reference registered "
                            + "(" + provider + "/" + providerDepositId + "); cannot register a different one "
                            + "(" + command.provider() + "/" + command.providerDepositId().value() + ")");
        }
        apply(new DepositProviderReferenceRegisteredEvent(
                depositId,
                command.provider().name(),
                command.providerDepositId().value(),
                command.actionUrl()));
    }

    @CommandHandler
    public void handle(ApplyProviderDepositUpdateCommand command) {
        boolean referenceMatches = providerDepositId != null
                && providerDepositId.equals(command.providerDepositId().value())
                && provider == command.provider();
        if (!referenceMatches) {
            throw new ProviderReferenceMismatchException(
                    "Update for deposit " + depositId + " does not match its registered provider reference "
                            + "(expected " + provider + "/" + providerDepositId + ", got "
                            + command.provider() + "/" + command.providerDepositId().value() + ")");
        }

        DepositStatus target = DepositStatus.valueOf(command.status().name());

        if (isTerminal(status)) {
            if (status == target) {
                return; // rule 7: idempotent repetition of a terminal outcome
            }
            throw new TerminalStateTransitionException(
                    "Deposit " + depositId + " is already terminal (" + status
                            + "); cannot transition to " + target);
        }

        switch (target) {
            case ACTION_REQUIRED -> apply(new DepositActionRequiredEvent(
                    depositId, accountId, amountMinor, currency.name(), provider.name()));
            case PROCESSING -> apply(new DepositProcessingStartedEvent(
                    depositId, accountId, amountMinor, currency.name(), provider.name()));
            case SUCCEEDED -> apply(new DepositSucceededEvent(
                    depositId, accountId, amountMinor, currency.name(), provider.name()));
            case FAILED -> apply(new DepositFailedEvent(
                    depositId, accountId, amountMinor, currency.name(), provider.name(),
                    command.failureReason().name()));
            case CANCELLED -> apply(new DepositCancelledEvent(
                    depositId, accountId, amountMinor, currency.name(), provider.name(),
                    command.cancellationReason()));
            default -> throw new IllegalStateException("PENDING is never observed externally: " + target);
        }
    }

    @EventSourcingHandler
    public void on(DepositInitiatedEvent event) {
        this.depositId = event.depositId();
        this.accountId = event.accountId();
        this.amountMinor = event.amountMinor();
        this.currency = Currency.valueOf(event.currency());
        this.provider = Provider.valueOf(event.provider());
        this.status = DepositStatus.PENDING;
    }

    @EventSourcingHandler
    public void on(DepositProviderReferenceRegisteredEvent event) {
        this.providerDepositId = event.providerDepositId();
    }

    @EventSourcingHandler
    public void on(DepositActionRequiredEvent event) {
        this.status = DepositStatus.ACTION_REQUIRED;
    }

    @EventSourcingHandler
    public void on(DepositProcessingStartedEvent event) {
        this.status = DepositStatus.PROCESSING;
    }

    @EventSourcingHandler
    public void on(DepositSucceededEvent event) {
        this.status = DepositStatus.SUCCEEDED;
    }

    @EventSourcingHandler
    public void on(DepositFailedEvent event) {
        this.status = DepositStatus.FAILED;
        this.failureReason = FailureReason.valueOf(event.failureReason());
    }

    @EventSourcingHandler
    public void on(DepositCancelledEvent event) {
        this.status = DepositStatus.CANCELLED;
    }

    private static boolean isTerminal(DepositStatus status) {
        return status == DepositStatus.SUCCEEDED
                || status == DepositStatus.FAILED
                || status == DepositStatus.CANCELLED;
    }
}
