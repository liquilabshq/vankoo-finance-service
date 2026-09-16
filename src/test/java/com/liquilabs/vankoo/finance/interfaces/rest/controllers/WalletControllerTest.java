package com.liquilabs.vankoo.finance.interfaces.rest.controllers;

import com.liquilabs.vankoo.finance.domain.exceptions.CommandRejectedException;
import com.liquilabs.vankoo.finance.domain.exceptions.IdempotencyKeyConflictException;
import com.liquilabs.vankoo.finance.domain.model.commands.DebitWalletCommand;
import com.liquilabs.vankoo.finance.domain.model.queries.WalletBalance;
import com.liquilabs.vankoo.finance.domain.model.queries.WalletMovement;
import com.liquilabs.vankoo.finance.domain.model.queries.WalletMovementPage;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DebitId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.IdempotencyKey;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.MovementDirection;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletMovementKind;
import com.liquilabs.vankoo.finance.domain.services.WalletCommandService;
import com.liquilabs.vankoo.finance.domain.services.WalletQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code WalletController} depends on {@link WalletCommandService} and
 * {@link WalletQueryService}, both mocked, so Axon/JPA never load. The
 * {@code @WebMvcTest} slice does pick up {@code WebMvcConfiguration} and
 * {@code FinanceExceptionHandler}, so the {@code X-User-Id} guard and the
 * problem+json bodies are exercised for real here.
 */
@WebMvcTest(WalletController.class)
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletCommandService walletCommandService;

    @MockitoBean
    private WalletQueryService walletQueryService;

    private static final String ACCOUNT_ID = UUID.randomUUID().toString();
    private static final String SOMEONE_ELSE = UUID.randomUUID().toString();
    private static final String CALLER_HEADER = "X-User-Id";
    private static final String DEBIT_BODY = "{\"amountMinor\": 5000, \"reason\": \"INVERSION\"}";

    // --- debitWallet ---------------------------------------------------------

    @Test
    void debitWallet_firstTime_respondsCreatedWithTheMintedDebitId() throws Exception {
        // The service answers whatever id the command carried — a first call.
        when(walletCommandService.handle(any(), any(), any()))
                .thenAnswer(invocation -> invocation.<DebitWalletCommand>getArgument(0).debitId());

        mockMvc.perform(debit(ACCOUNT_ID).header(CALLER_HEADER, ACCOUNT_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId", is(ACCOUNT_ID)))
                .andExpect(jsonPath("$.walletId", is(WalletId.derive(AccountId.of(ACCOUNT_ID), Currency.PEN).toString())))
                .andExpect(jsonPath("$.currency", is("PEN")))
                .andExpect(jsonPath("$.amountMinor", is(5000)))
                .andExpect(jsonPath("$.reason", is("INVERSION")))
                .andExpect(jsonPath("$.debitId").isNotEmpty());

        ArgumentCaptor<DebitWalletCommand> command = ArgumentCaptor.forClass(DebitWalletCommand.class);
        verify(walletCommandService).handle(command.capture(), eq(AccountId.of(ACCOUNT_ID)), eq(new IdempotencyKey("k1")));
        assertEquals(5_000L, command.getValue().amount().amountMinor());
        assertEquals(Currency.PEN, command.getValue().amount().currency());
    }

    @Test
    void debitWallet_replay_respondsCreatedWithTheOriginalDebitId() throws Exception {
        DebitId original = new DebitId();
        when(walletCommandService.handle(any(), any(), any())).thenReturn(original);

        mockMvc.perform(debit(ACCOUNT_ID).header(CALLER_HEADER, ACCOUNT_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.debitId", is(original.toString())));

        ArgumentCaptor<DebitWalletCommand> command = ArgumentCaptor.forClass(DebitWalletCommand.class);
        verify(walletCommandService).handle(command.capture(), any(), any());
        assertNotEquals(original, command.getValue().debitId(), "the request minted its own id; the replay overrode it");
    }

    @Test
    void debitWallet_insufficientBalance_respondsConflictWithCode() throws Exception {
        when(walletCommandService.handle(any(), any(), any()))
                .thenThrow(new CommandRejectedException("insufficient-balance", "Wallet x has balance 100"));

        mockMvc.perform(debit(ACCOUNT_ID).header(CALLER_HEADER, ACCOUNT_ID))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code", is("insufficient-balance")))
                .andExpect(jsonPath("$.type", is("https://docs.vankoo.dev/errors/insufficient-balance")))
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.detail", is("Wallet x has balance 100")));
    }

    @Test
    void debitWallet_walletNeverOpened_respondsNotFoundWithCode() throws Exception {
        when(walletCommandService.handle(any(), any(), any()))
                .thenThrow(new CommandRejectedException("wallet-not-found", "no such aggregate"));

        mockMvc.perform(debit(ACCOUNT_ID).header(CALLER_HEADER, ACCOUNT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("wallet-not-found")));
    }

    @Test
    void debitWallet_conflictingIdempotencyKey_respondsConflictWithCode() throws Exception {
        when(walletCommandService.handle(any(), any(), any()))
                .thenThrow(new IdempotencyKeyConflictException("reused with different content"));

        mockMvc.perform(debit(ACCOUNT_ID).header(CALLER_HEADER, ACCOUNT_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("idempotency-key-conflict")));
    }

    @Test
    void debitWallet_missingIdempotencyKeyHeader_respondsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{accountId}/wallets/{currency}/debits", ACCOUNT_ID, "PEN")
                        .header(CALLER_HEADER, ACCOUNT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DEBIT_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("invalid-request")));

        verify(walletCommandService, never()).handle(any(), any(), any());
    }

    @Test
    void debitWallet_nonPositiveAmount_respondsBadRequestNamingTheField() throws Exception {
        mockMvc.perform(debit(ACCOUNT_ID).header(CALLER_HEADER, ACCOUNT_ID)
                        .content("{\"amountMinor\": 0, \"reason\": \"INVERSION\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("validation-failed")))
                .andExpect(jsonPath("$.errors[0].field", is("amountMinor")));

        verify(walletCommandService, never()).handle(any(), any(), any());
    }

    @Test
    void debitWallet_unknownReason_respondsBadRequest() throws Exception {
        mockMvc.perform(debit(ACCOUNT_ID).header(CALLER_HEADER, ACCOUNT_ID)
                        .content("{\"amountMinor\": 5000, \"reason\": \"PROPINA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("invalid-request")));

        verify(walletCommandService, never()).handle(any(), any(), any());
    }

    @Test
    void debitWallet_unsupportedCurrency_respondsBadRequest() throws Exception {
        mockMvc.perform(debit(ACCOUNT_ID, "EUR").header(CALLER_HEADER, ACCOUNT_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("invalid-request")));
    }

    @Test
    void debitWallet_callerMismatch_respondsForbiddenWithoutDispatching() throws Exception {
        mockMvc.perform(debit(ACCOUNT_ID).header(CALLER_HEADER, SOMEONE_ELSE))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code", is("forbidden")));

        verify(walletCommandService, never()).handle(any(), any(), any());
    }

    @Test
    void debitWallet_missingCaller_respondsForbidden() throws Exception {
        mockMvc.perform(debit(ACCOUNT_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("forbidden")));

        verify(walletCommandService, never()).handle(any(), any(), any());
    }

    // --- getWalletBalance ----------------------------------------------------

    @Test
    void getWalletBalance_found_respondsOk() throws Exception {
        when(walletQueryService.getWalletBalance(any())).thenReturn(Optional.of(balance()));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}", ACCOUNT_ID, "PEN")
                        .header(CALLER_HEADER, ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", is(ACCOUNT_ID)))
                .andExpect(jsonPath("$.balanceMinor", is(12500)));
    }

    @Test
    void getWalletBalance_notFound_respondsNotFoundWithCode() throws Exception {
        when(walletQueryService.getWalletBalance(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}", ACCOUNT_ID, "PEN")
                        .header(CALLER_HEADER, ACCOUNT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("wallet-not-found")));
    }

    @Test
    void getWalletBalance_malformedAccountId_respondsBadRequest() throws Exception {
        // The guard leaves a malformed path alone; the format error wins.
        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}", "not-a-uuid", "PEN")
                        .header(CALLER_HEADER, ACCOUNT_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("invalid-request")));
    }

    @Test
    void getWalletBalance_unsupportedCurrency_respondsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}", ACCOUNT_ID, "EUR")
                        .header(CALLER_HEADER, ACCOUNT_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getWalletBalance_callerMismatch_respondsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}", ACCOUNT_ID, "PEN")
                        .header(CALLER_HEADER, SOMEONE_ELSE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("forbidden")));

        verify(walletQueryService, never()).getWalletBalance(any());
    }

    @Test
    void getWalletBalance_callerHeaderCaseInsensitive_respondsOk() throws Exception {
        when(walletQueryService.getWalletBalance(any())).thenReturn(Optional.of(balance()));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}", ACCOUNT_ID, "PEN")
                        .header(CALLER_HEADER, ACCOUNT_ID.toUpperCase()))
                .andExpect(status().isOk());
    }

    // --- listWalletMovements -------------------------------------------------

    @Test
    void listWalletMovements_respondsOkWithPage() throws Exception {
        DebitId debitId = new DebitId();
        WalletMovement credit = new WalletMovement(
                WalletMovementKind.RECARGA, MovementDirection.CREDIT,
                new Money(12_500L, Currency.PEN), DepositId.of(UUID.randomUUID().toString()), null, Instant.now());
        WalletMovement debit = new WalletMovement(
                WalletMovementKind.INVERSION, MovementDirection.DEBIT,
                new Money(5_000L, Currency.PEN), null, debitId, Instant.now());
        when(walletQueryService.listWalletMovements(any()))
                .thenReturn(new WalletMovementPage(List.of(debit, credit), 0, 20, 2L));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}/movements", ACCOUNT_ID, "PEN")
                        .header(CALLER_HEADER, ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.items[0].type", is("INVERSION")))
                .andExpect(jsonPath("$.items[0].debitId", is(debitId.toString())))
                .andExpect(jsonPath("$.items[0].sourceDepositId", nullValue()))
                .andExpect(jsonPath("$.items[1].type", is("RECARGA")))
                .andExpect(jsonPath("$.items[1].debitId", nullValue()));
    }

    @Test
    void listWalletMovements_walletNeverOpened_respondsOkWithEmptyPage() throws Exception {
        when(walletQueryService.listWalletMovements(any()))
                .thenReturn(new WalletMovementPage(List.of(), 0, 20, 0L));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}/movements", ACCOUNT_ID, "PEN")
                        .header(CALLER_HEADER, ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", is(0)));
    }

    @Test
    void listWalletMovements_negativePage_respondsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}/movements", ACCOUNT_ID, "PEN")
                        .header(CALLER_HEADER, ACCOUNT_ID)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("invalid-request")));
    }

    @Test
    void listWalletMovements_nonPositiveSize_respondsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}/movements", ACCOUNT_ID, "PEN")
                        .header(CALLER_HEADER, ACCOUNT_ID)
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listWalletMovements_callerMismatch_respondsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/wallets/{currency}/movements", ACCOUNT_ID, "PEN")
                        .header(CALLER_HEADER, SOMEONE_ELSE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("forbidden")));

        verify(walletQueryService, never()).listWalletMovements(any());
    }

    private static MockHttpServletRequestBuilder debit(String accountId) {
        return debit(accountId, "PEN");
    }

    private static MockHttpServletRequestBuilder debit(String accountId, String currency) {
        return post("/api/v1/accounts/{accountId}/wallets/{currency}/debits", accountId, currency)
                .header("Idempotency-Key", "k1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(DEBIT_BODY);
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
