package com.liquilabs.vankoo.finance.interfaces.rest.controllers;

import com.liquilabs.vankoo.finance.domain.model.queries.WalletBalance;
import com.liquilabs.vankoo.finance.domain.model.queries.WalletMovement;
import com.liquilabs.vankoo.finance.domain.model.queries.WalletMovementPage;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.MovementDirection;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletMovementKind;
import com.liquilabs.vankoo.finance.domain.services.WalletQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code WalletController} depends only on {@link WalletQueryService}, so it
 * is mocked and Axon/JPA never load.
 */
@WebMvcTest(WalletController.class)
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletQueryService walletQueryService;

    private static final String ACCOUNT_ID = UUID.randomUUID().toString();

    @Test
    void getWalletBalance_found_respondsOk() throws Exception {
        when(walletQueryService.getWalletBalance(any())).thenReturn(Optional.of(balance()));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}", ACCOUNT_ID, "PEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", is(ACCOUNT_ID)))
                .andExpect(jsonPath("$.balanceMinor", is(12500)));
    }

    @Test
    void getWalletBalance_notFound_respondsNotFound() throws Exception {
        when(walletQueryService.getWalletBalance(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}", ACCOUNT_ID, "PEN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getWalletBalance_malformedAccountId_respondsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}", "not-a-uuid", "PEN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getWalletBalance_unsupportedCurrency_respondsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}", ACCOUNT_ID, "EUR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listWalletMovements_respondsOkWithPage() throws Exception {
        WalletMovement movement = new WalletMovement(
                WalletMovementKind.RECARGA, MovementDirection.CREDIT,
                new Money(12_500L, Currency.PEN), DepositId.of(UUID.randomUUID().toString()), Instant.now());
        when(walletQueryService.listWalletMovements(any()))
                .thenReturn(new WalletMovementPage(List.of(movement), 0, 20, 1L));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}/movements", ACCOUNT_ID, "PEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.items[0].type", is("RECARGA")));
    }

    @Test
    void listWalletMovements_walletNeverOpened_respondsOkWithEmptyPage() throws Exception {
        when(walletQueryService.listWalletMovements(any()))
                .thenReturn(new WalletMovementPage(List.of(), 0, 20, 0L));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}/movements", ACCOUNT_ID, "PEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", is(0)));
    }

    @Test
    void listWalletMovements_negativePage_respondsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}/movements", ACCOUNT_ID, "PEN")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listWalletMovements_nonPositiveSize_respondsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}/movements", ACCOUNT_ID, "PEN")
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    private static WalletBalance balance() {
        Instant now = Instant.now();
        return new WalletBalance(
                WalletId.derive(AccountId.of(ACCOUNT_ID), Currency.PEN),
                AccountId.of(ACCOUNT_ID),
                new Money(12_500L, Currency.PEN),
                now, now);
    }
}
