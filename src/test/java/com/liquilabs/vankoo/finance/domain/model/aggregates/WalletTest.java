package com.liquilabs.vankoo.finance.domain.model.aggregates;

import com.liquilabs.vankoo.finance.domain.model.commands.CreditWalletCommand;
import com.liquilabs.vankoo.finance.domain.model.commands.DebitWalletCommand;
import com.liquilabs.vankoo.finance.domain.model.commands.OpenWalletCommand;
import com.liquilabs.vankoo.finance.domain.model.events.WalletCreditedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.WalletDebitedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.WalletOpenedEvent;
import com.liquilabs.vankoo.finance.domain.exceptions.InsufficientBalanceException;
import com.liquilabs.vankoo.finance.domain.exceptions.InvalidCreditAmountException;
import com.liquilabs.vankoo.finance.domain.exceptions.InvalidDebitAmountException;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DebitId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletMovementType;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WalletTest {

    private static final AccountId ACCOUNT_ID = AccountId.of("018f8f2e-0000-7000-8000-000000000001");
    private static final WalletId WALLET_ID = WalletId.derive(ACCOUNT_ID, Currency.PEN);
    private static final DepositId SOURCE_DEPOSIT_ID = new DepositId();
    private static final DebitId DEBIT_ID = new DebitId();

    private FixtureConfiguration<Wallet> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Wallet.class);
    }

    @Test
    void opensAWalletSuccessfully() {
        fixture.givenNoPriorActivity()
                .when(new OpenWalletCommand(WALLET_ID, ACCOUNT_ID, Currency.PEN))
                .expectEvents(new WalletOpenedEvent(WALLET_ID.toString(), ACCOUNT_ID.toString(), "PEN"));
    }

    @Test
    void creditsAnOpenWallet() {
        fixture.given(openedEvent())
                .when(new CreditWalletCommand(WALLET_ID, new Money(12_500L, Currency.PEN), SOURCE_DEPOSIT_ID))
                .expectEvents(new WalletCreditedEvent(
                        WALLET_ID.toString(), 12_500L, "PEN", SOURCE_DEPOSIT_ID.toString()));
    }

    @Test
    void rejectsNonPositiveCreditAmount() {
        Money invalidAmount = new Money(0L, Currency.PEN);
        fixture.given(openedEvent())
                .when(new CreditWalletCommand(WALLET_ID, invalidAmount, SOURCE_DEPOSIT_ID))
                .expectException(InvalidCreditAmountException.class);
    }

    @Test
    void debitsAWalletWithSufficientBalance() {
        fixture.given(openedEvent(), creditedEvent(12_500L))
                .when(new DebitWalletCommand(WALLET_ID, DEBIT_ID, new Money(5_000L, Currency.PEN), WalletMovementType.INVERSION))
                .expectEvents(new WalletDebitedEvent(WALLET_ID.toString(), 5_000L, "PEN", "INVERSION", DEBIT_ID.toString()));
    }

    @Test
    void debitsCarryTheirDebitIdInTheEvent() {
        DebitId anotherDebit = new DebitId();
        fixture.given(openedEvent(), creditedEvent(12_500L))
                .when(new DebitWalletCommand(WALLET_ID, anotherDebit, new Money(1L, Currency.PEN), WalletMovementType.INVERSION))
                .expectEvents(new WalletDebitedEvent(WALLET_ID.toString(), 1L, "PEN", "INVERSION", anotherDebit.toString()));
    }

    @Test
    void rejectsADebitThatExceedsTheBalance() {
        fixture.given(openedEvent(), creditedEvent(5_000L))
                .when(new DebitWalletCommand(WALLET_ID, DEBIT_ID, new Money(5_001L, Currency.PEN), WalletMovementType.RETIRO))
                .expectException(InsufficientBalanceException.class);
    }

    @Test
    void rejectsNonPositiveDebitAmount() {
        Money invalidAmount = new Money(0L, Currency.PEN);
        fixture.given(openedEvent(), creditedEvent(12_500L))
                .when(new DebitWalletCommand(WALLET_ID, DEBIT_ID, invalidAmount, WalletMovementType.COMISION))
                .expectException(InvalidDebitAmountException.class);
    }

    private static WalletOpenedEvent openedEvent() {
        return new WalletOpenedEvent(WALLET_ID.toString(), ACCOUNT_ID.toString(), "PEN");
    }

    private static WalletCreditedEvent creditedEvent(long amountMinor) {
        return new WalletCreditedEvent(WALLET_ID.toString(), amountMinor, "PEN", SOURCE_DEPOSIT_ID.toString());
    }
}
