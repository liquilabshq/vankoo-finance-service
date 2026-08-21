package com.liquilabs.vankoo.finance.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class WalletIdTest {

    private static final AccountId ACCOUNT_ID = AccountId.of("018f8f2e-0000-7000-8000-000000000001");
    private static final AccountId OTHER_ACCOUNT_ID = AccountId.of("018f8f2e-0000-7000-8000-000000000002");

    @Test
    void derivationIsStableForTheSameInputs() {
        assertEquals(WalletId.derive(ACCOUNT_ID, Currency.PEN), WalletId.derive(ACCOUNT_ID, Currency.PEN));
    }

    @Test
    void derivationDiffersAcrossCurrenciesForTheSameAccount() {
        assertNotEquals(WalletId.derive(ACCOUNT_ID, Currency.PEN), WalletId.derive(ACCOUNT_ID, Currency.USD));
    }

    @Test
    void derivationDiffersAcrossAccountsForTheSameCurrency() {
        assertNotEquals(
                WalletId.derive(ACCOUNT_ID, Currency.PEN), WalletId.derive(OTHER_ACCOUNT_ID, Currency.PEN));
    }
}
