package com.liquilabs.vankoo.finance.interfaces.rest.controllers;

import com.liquilabs.vankoo.finance.domain.exceptions.IdempotencyKeyConflictException;
import com.liquilabs.vankoo.finance.domain.exceptions.InvalidDepositAmountException;
import com.liquilabs.vankoo.finance.domain.exceptions.UnsupportedCurrencyException;
import com.liquilabs.vankoo.finance.domain.model.commands.InitiateDepositCommand;
import com.liquilabs.vankoo.finance.domain.model.queries.DepositSummaryPage;
import com.liquilabs.vankoo.finance.domain.model.queries.GetDepositByIdQuery;
import com.liquilabs.vankoo.finance.domain.model.queries.ListDepositsByAccountQuery;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.services.DepositCommandService;
import com.liquilabs.vankoo.finance.domain.services.DepositQueryService;
import com.liquilabs.vankoo.finance.interfaces.rest.resources.CreateDepositResource;
import com.liquilabs.vankoo.finance.interfaces.rest.resources.DepositResource;
import com.liquilabs.vankoo.finance.interfaces.rest.resources.DepositSummaryPageResource;
import com.liquilabs.vankoo.finance.interfaces.rest.transform.CreateDepositCommandFromResourceAssembler;
import com.liquilabs.vankoo.finance.interfaces.rest.transform.DepositResourceFromSummaryAssembler;
import com.liquilabs.vankoo.finance.interfaces.rest.transform.DepositSummaryPageResourceFromPageAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Finance's own API for {@code Deposit} — unlike
 * {@code StripeWebhookController}, this is a client-facing surface.
 *
 * <p>The controller holds no business rules. It validates the request format,
 * delegates to {@link DepositCommandService}/{@link DepositQueryService}, and
 * translates the answer back. It never touches a repository or Axon directly.
 *
 * <p>No {@code @RestControllerAdvice} exists anywhere in this codebase, and
 * the common HTTP error format for Vankoo is still an open decision in the
 * contract — so, like {@code StripeWebhookController}, errors are mapped
 * inline, per exception, with no response body.
 */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Deposits", description = "Initiate and query an investor's top-ups.")
public class DepositController {

    private final DepositCommandService depositCommandService;
    private final DepositQueryService depositQueryService;

    public DepositController(DepositCommandService depositCommandService, DepositQueryService depositQueryService) {
        this.depositCommandService = depositCommandService;
        this.depositQueryService = depositQueryService;
    }

    @PostMapping(value = "/deposits", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Initiate a deposit",
            description = "Accepts the request and returns immediately with the deposit in PENDING. Creating the "
                    + "charge with the provider and updating the Read Model happen afterwards.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Accepted — or replayed, if Idempotency-Key repeats"),
            @ApiResponse(responseCode = "400", description = "Malformed request, or an invalid amount"),
            @ApiResponse(responseCode = "409", description = "Idempotency-Key reused with different content")
    })
    public ResponseEntity<DepositResource> createDeposit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateDepositResource resource) {
        InitiateDepositCommand command;
        try {
            command = CreateDepositCommandFromResourceAssembler.toCommandFromResource(resource, idempotencyKey);
        } catch (IllegalArgumentException | UnsupportedCurrencyException exception) {
            return ResponseEntity.badRequest().build();
        }

        DepositId resolvedId;
        try {
            resolvedId = depositCommandService.handle(command);
        } catch (InvalidDepositAmountException exception) {
            return ResponseEntity.badRequest().build();
        } catch (IdempotencyKeyConflictException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        if (resolvedId.equals(command.depositId())) {
            // First time this key was seen: the projection may not have caught
            // up yet, so the response is built from the command's own inputs.
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(CreateDepositCommandFromResourceAssembler.toPendingResourceFrom(resolvedId, resource));
        }

        // Replay: this deposit has existed for a while, so querying its real
        // current status is safe here — the race above does not apply.
        return depositQueryService.getDepositById(new GetDepositByIdQuery(resolvedId))
                .map(summary -> ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(DepositResourceFromSummaryAssembler.toResourceFromSummary(summary)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(CreateDepositCommandFromResourceAssembler.toPendingResourceFrom(resolvedId, resource)));
    }

    @GetMapping("/deposits/{depositId}")
    @Operation(summary = "Get a deposit's projected state")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "400", description = "depositId is not a valid identifier"),
            @ApiResponse(responseCode = "404", description = "No deposit with that id")
    })
    public ResponseEntity<DepositResource> getDeposit(@PathVariable String depositId) {
        DepositId id;
        try {
            id = DepositId.of(depositId);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }

        return depositQueryService.getDepositById(new GetDepositByIdQuery(id))
                .map(summary -> ResponseEntity.ok(DepositResourceFromSummaryAssembler.toResourceFromSummary(summary)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/accounts/{accountId}/deposits")
    @Operation(summary = "List an account's deposits, paginated")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page, possibly empty"),
            @ApiResponse(responseCode = "400", description = "accountId is not a valid identifier, or page/size is invalid")
    })
    public ResponseEntity<DepositSummaryPageResource> listDeposits(
            @PathVariable String accountId,
            @Parameter(description = "0-based") @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AccountId id;
        try {
            id = AccountId.of(accountId);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
        if (page < 0 || size <= 0) {
            return ResponseEntity.badRequest().build();
        }

        DepositSummaryPage result =
                depositQueryService.listDepositsByAccount(new ListDepositsByAccountQuery(id, null, page, size));
        return ResponseEntity.ok(DepositSummaryPageResourceFromPageAssembler.toResourceFromPage(result));
    }
}
