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
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.FailureReason;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.IdempotencyKey;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.NormalizedDepositStatus;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderEventId;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

class DepositTest {

    private static final DepositId DEPOSIT_ID = new DepositId();
    private static final AccountId ACCOUNT_ID = AccountId.of("018f8f2e-0000-7000-8000-000000000001");
    private static final Money AMOUNT = new Money(12_500L, Currency.PEN);
    private static final IdempotencyKey IDEMPOTENCY_KEY = new IdempotencyKey("client-key-1");
    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final ProviderDepositId PROVIDER_DEPOSIT_ID = new ProviderDepositId("cs_test_123");
    private static final ProviderDepositId OTHER_PROVIDER_DEPOSIT_ID = new ProviderDepositId("cs_test_999");

    private FixtureConfiguration<Deposit> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Deposit.class);
    }

    @Test
    void initiatesADepositSuccessfully() {
        fixture.givenNoPriorActivity()
                .when(new InitiateDepositCommand(
                        DEPOSIT_ID, ACCOUNT_ID, AMOUNT, Provider.STRIPE, IDEMPOTENCY_KEY, NOW, "Top-up"))
                .expectEvents(new DepositInitiatedEvent(
                        DEPOSIT_ID.toString(), ACCOUNT_ID.toString(), 12_500L, "PEN", "STRIPE",
                        "client-key-1", "Top-up"));
    }

    @Test
    void rejectsNonPositiveAmount() {
        Money invalidAmount = new Money(0L, Currency.PEN);
        fixture.givenNoPriorActivity()
                .when(new InitiateDepositCommand(
                        DEPOSIT_ID, ACCOUNT_ID, invalidAmount, Provider.STRIPE, IDEMPOTENCY_KEY, NOW, null))
                .expectException(InvalidDepositAmountException.class);
    }

    @Test
    void registersTheProviderReferenceForTheFirstTime() {
        fixture.given(initiatedEvent())
                .when(new RegisterDepositProviderReferenceCommand(
                        DEPOSIT_ID, Provider.STRIPE, PROVIDER_DEPOSIT_ID, NOW, "https://stripe.example/pay"))
                .expectEvents(new DepositProviderReferenceRegisteredEvent(
                        DEPOSIT_ID.toString(), "STRIPE", "cs_test_123", "https://stripe.example/pay"));
    }

    @Test
    void repeatingTheSameProviderReferenceIsIdempotent() {
        fixture.given(initiatedEvent(), registeredEvent())
                .when(new RegisterDepositProviderReferenceCommand(
                        DEPOSIT_ID, Provider.STRIPE, PROVIDER_DEPOSIT_ID, NOW, null))
                .expectEvents(); // no new event
    }

    @Test
    void registeringAConflictingProviderReferenceIsRejected() {
        fixture.given(initiatedEvent(), registeredEvent())
                .when(new RegisterDepositProviderReferenceCommand(
                        DEPOSIT_ID, Provider.STRIPE, OTHER_PROVIDER_DEPOSIT_ID, NOW, null))
                .expectException(ProviderReferenceMismatchException.class);
    }

    @Test
    void transitionsToActionRequired() {
        fixture.given(initiatedEvent(), registeredEvent())
                .when(applyUpdate(NormalizedDepositStatus.ACTION_REQUIRED, null, null))
                .expectEvents(new DepositActionRequiredEvent(
                        DEPOSIT_ID.toString(), ACCOUNT_ID.toString(), 12_500L, "PEN", "STRIPE"));
    }

    @Test
    void transitionsToProcessing() {
        fixture.given(initiatedEvent(), registeredEvent())
                .when(applyUpdate(NormalizedDepositStatus.PROCESSING, null, null))
                .expectEvents(new DepositProcessingStartedEvent(
                        DEPOSIT_ID.toString(), ACCOUNT_ID.toString(), 12_500L, "PEN", "STRIPE"));
    }

    @Test
    void transitionsToSucceeded() {
        fixture.given(initiatedEvent(), registeredEvent())
                .when(applyUpdate(NormalizedDepositStatus.SUCCEEDED, null, null))
                .expectEvents(new DepositSucceededEvent(
                        DEPOSIT_ID.toString(), ACCOUNT_ID.toString(), 12_500L, "PEN", "STRIPE"));
    }

    @Test
    void transitionsToFailedWithReason() {
        fixture.given(initiatedEvent(), registeredEvent())
                .when(applyUpdate(NormalizedDepositStatus.FAILED, FailureReason.DECLINED, null))
                .expectEvents(new DepositFailedEvent(
                        DEPOSIT_ID.toString(), ACCOUNT_ID.toString(), 12_500L, "PEN", "STRIPE", "DECLINED"));
    }

    @Test
    void transitionsToCancelledWithReason() {
        fixture.given(initiatedEvent(), registeredEvent())
                .when(applyUpdate(NormalizedDepositStatus.CANCELLED, null, "expired"))
                .expectEvents(new DepositCancelledEvent(
                        DEPOSIT_ID.toString(), ACCOUNT_ID.toString(), 12_500L, "PEN", "STRIPE", "expired"));
    }

    @Test
    void rejectsAnUpdateWhoseProviderReferenceDoesNotMatch() {
        fixture.given(initiatedEvent(), registeredEvent())
                .when(new ApplyProviderDepositUpdateCommand(
                        DEPOSIT_ID, Provider.STRIPE, OTHER_PROVIDER_DEPOSIT_ID, new ProviderEventId("evt_1"),
                        NormalizedDepositStatus.SUCCEEDED, NOW, null, null))
                .expectException(ProviderReferenceMismatchException.class);
    }

    @Test
    void rejectsAnUpdateBeforeAnyProviderReferenceIsRegistered() {
        fixture.given(initiatedEvent())
                .when(applyUpdate(NormalizedDepositStatus.SUCCEEDED, null, null))
                .expectException(ProviderReferenceMismatchException.class);
    }

    @Test
    void repeatingTheSameTerminalOutcomeIsIdempotent() {
        fixture.given(initiatedEvent(), registeredEvent(), succeededEvent())
                .when(applyUpdate(NormalizedDepositStatus.SUCCEEDED, null, null))
                .expectEvents(); // no new event
    }

    @Test
    void movingFromOneTerminalStateToADifferentOneIsRejected() {
        fixture.given(initiatedEvent(), registeredEvent(), succeededEvent())
                .when(applyUpdate(NormalizedDepositStatus.FAILED, FailureReason.PROVIDER_ERROR, null))
                .expectException(TerminalStateTransitionException.class);
    }

    @Test
    void reconstructsFullyFromItsEventHistoryBeforeAcceptingACommand() {
        // Three prior events, none of them the constructor event: only a pure
        // replay through @EventSourcingHandler methods can produce an aggregate
        // that correctly accepts this command afterward.
        fixture.given(
                        initiatedEvent(),
                        registeredEvent(),
                        new DepositActionRequiredEvent(
                                DEPOSIT_ID.toString(), ACCOUNT_ID.toString(), 12_500L, "PEN", "STRIPE"))
                .when(applyUpdate(NormalizedDepositStatus.PROCESSING, null, null))
                .expectEvents(new DepositProcessingStartedEvent(
                        DEPOSIT_ID.toString(), ACCOUNT_ID.toString(), 12_500L, "PEN", "STRIPE"));
    }

    private static DepositInitiatedEvent initiatedEvent() {
        return new DepositInitiatedEvent(
                DEPOSIT_ID.toString(), ACCOUNT_ID.toString(), 12_500L, "PEN", "STRIPE",
                "client-key-1", "Top-up");
    }

    private static DepositProviderReferenceRegisteredEvent registeredEvent() {
        return new DepositProviderReferenceRegisteredEvent(
                DEPOSIT_ID.toString(), "STRIPE", "cs_test_123", "https://stripe.example/pay");
    }

    private static DepositSucceededEvent succeededEvent() {
        return new DepositSucceededEvent(DEPOSIT_ID.toString(), ACCOUNT_ID.toString(), 12_500L, "PEN", "STRIPE");
    }

    private static ApplyProviderDepositUpdateCommand applyUpdate(
            NormalizedDepositStatus status, FailureReason failureReason, String cancellationReason) {
        return new ApplyProviderDepositUpdateCommand(
                DEPOSIT_ID, Provider.STRIPE, PROVIDER_DEPOSIT_ID, new ProviderEventId("evt_1"),
                status, NOW, failureReason, cancellationReason);
    }
}
