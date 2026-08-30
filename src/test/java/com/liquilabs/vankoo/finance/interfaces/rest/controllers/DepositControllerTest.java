package com.liquilabs.vankoo.finance.interfaces.rest.controllers;

import com.liquilabs.vankoo.finance.domain.exceptions.IdempotencyKeyConflictException;
import com.liquilabs.vankoo.finance.domain.exceptions.InvalidDepositAmountException;
import com.liquilabs.vankoo.finance.domain.model.queries.DepositSummary;
import com.liquilabs.vankoo.finance.domain.model.queries.DepositSummaryPage;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositStatus;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import com.liquilabs.vankoo.finance.domain.services.DepositCommandService;
import com.liquilabs.vankoo.finance.domain.services.DepositQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code DepositController} depends only on {@link DepositCommandService} and
 * {@link DepositQueryService}, so both are mocked and Axon/JPA never load.
 */
@WebMvcTest(DepositController.class)
class DepositControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DepositCommandService depositCommandService;

    @MockitoBean
    private DepositQueryService depositQueryService;

    private static final String ACCOUNT_ID = UUID.randomUUID().toString();

    @Test
    void createDeposit_firstTime_respondsAcceptedWithPending() throws Exception {
        when(depositCommandService.handle(any())).thenAnswer(invocation -> {
            var command = invocation.getArgument(0, com.liquilabs.vankoo.finance.domain.model.commands.InitiateDepositCommand.class);
            return command.depositId();
        });

        mockMvc.perform(post("/v1/deposits")
                        .header("Idempotency-Key", "key-1")
                        .contentType("application/json")
                        .content(createDepositBody()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.accountId", is(ACCOUNT_ID)));

        verify(depositQueryService, never()).getDepositById(any());
    }

    @Test
    void createDeposit_replay_queriesAndReturnsRealStatus() throws Exception {
        DepositId existingId = new DepositId();
        when(depositCommandService.handle(any())).thenReturn(existingId);
        when(depositQueryService.getDepositById(any())).thenReturn(Optional.of(summaryWithStatus(existingId, DepositStatus.SUCCEEDED)));

        mockMvc.perform(post("/v1/deposits")
                        .header("Idempotency-Key", "key-1")
                        .contentType("application/json")
                        .content(createDepositBody()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("SUCCEEDED")));
    }

    @Test
    void createDeposit_invalidAmount_respondsBadRequest() throws Exception {
        when(depositCommandService.handle(any())).thenThrow(new InvalidDepositAmountException(-1));

        mockMvc.perform(post("/v1/deposits")
                        .header("Idempotency-Key", "key-1")
                        .contentType("application/json")
                        .content(createDepositBody()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDeposit_unsupportedCurrency_respondsBadRequest() throws Exception {
        // UnsupportedCurrencyException does not extend IllegalArgumentException,
        // so this exercises a distinct catch branch from the malformed-id cases.
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("accountId", ACCOUNT_ID);
            put("amountMinor", 12_500L);
            put("currency", "EUR");
            put("provider", "STRIPE");
            put("description", "Recarga de saldo");
        }});

        mockMvc.perform(post("/v1/deposits")
                        .header("Idempotency-Key", "key-1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(depositCommandService, never()).handle(any());
    }

    @Test
    void createDeposit_conflictingIdempotencyKey_respondsConflict() throws Exception {
        when(depositCommandService.handle(any())).thenThrow(new IdempotencyKeyConflictException("conflict"));

        mockMvc.perform(post("/v1/deposits")
                        .header("Idempotency-Key", "key-1")
                        .contentType("application/json")
                        .content(createDepositBody()))
                .andExpect(status().isConflict());
    }

    @Test
    void createDeposit_missingIdempotencyKeyHeader_respondsBadRequest() throws Exception {
        mockMvc.perform(post("/v1/deposits")
                        .contentType("application/json")
                        .content(createDepositBody()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDeposit_blankBody_respondsBadRequest() throws Exception {
        mockMvc.perform(post("/v1/deposits")
                        .header("Idempotency-Key", "key-1")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getDeposit_found_respondsOk() throws Exception {
        DepositId depositId = new DepositId();
        when(depositQueryService.getDepositById(any()))
                .thenReturn(Optional.of(summaryWithStatus(depositId, DepositStatus.PENDING)));

        mockMvc.perform(get("/v1/deposits/{depositId}", depositId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositId", is(depositId.toString())));
    }

    @Test
    void getDeposit_notFound_respondsNotFound() throws Exception {
        when(depositQueryService.getDepositById(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/deposits/{depositId}", new DepositId().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDeposit_malformedId_respondsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/deposits/{depositId}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listDeposits_respondsOkWithPage() throws Exception {
        DepositSummary summary = summaryWithStatus(new DepositId(), DepositStatus.SUCCEEDED);
        when(depositQueryService.listDepositsByAccount(any()))
                .thenReturn(new DepositSummaryPage(java.util.List.of(summary), 0, 20, 1L));

        mockMvc.perform(get("/v1/accounts/{accountId}/deposits", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.items[0].status", is("SUCCEEDED")));
    }

    @Test
    void listDeposits_negativePage_respondsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/accounts/{accountId}/deposits", ACCOUNT_ID).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    private String createDepositBody() throws Exception {
        return objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("accountId", ACCOUNT_ID);
            put("amountMinor", 12_500L);
            put("currency", "PEN");
            put("provider", "STRIPE");
            put("description", "Recarga de saldo");
        }});
    }

    private static DepositSummary summaryWithStatus(DepositId depositId, DepositStatus status) {
        return new DepositSummary(
                depositId,
                AccountId.of(ACCOUNT_ID),
                new Money(12_500L, Currency.PEN),
                Provider.STRIPE,
                null,
                "Recarga de saldo",
                status,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
    }
}
