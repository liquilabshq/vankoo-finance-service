package com.liquilabs.vankoo.finance.interfaces.rest.controllers;

import com.liquilabs.vankoo.finance.domain.exceptions.WalletNotFoundException;
import com.liquilabs.vankoo.finance.domain.model.commands.DebitWalletCommand;
import com.liquilabs.vankoo.finance.domain.model.queries.GetWalletBalanceQuery;
import com.liquilabs.vankoo.finance.domain.model.queries.ListWalletMovementsQuery;
import com.liquilabs.vankoo.finance.domain.model.queries.WalletMovementPage;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DebitId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.IdempotencyKey;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletId;
import com.liquilabs.vankoo.finance.domain.services.WalletCommandService;
import com.liquilabs.vankoo.finance.domain.services.WalletQueryService;
import com.liquilabs.vankoo.finance.interfaces.rest.resources.CreateWalletDebitResource;
import com.liquilabs.vankoo.finance.interfaces.rest.resources.WalletDebitResource;
import com.liquilabs.vankoo.finance.interfaces.rest.resources.WalletMovementPageResource;
import com.liquilabs.vankoo.finance.interfaces.rest.resources.WalletResource;
import com.liquilabs.vankoo.finance.interfaces.rest.transform.DebitWalletCommandFromResourceAssembler;
import com.liquilabs.vankoo.finance.interfaces.rest.transform.WalletMovementPageResourceFromPageAssembler;
import com.liquilabs.vankoo.finance.interfaces.rest.transform.WalletResourceFromBalanceAssembler;
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
 * Finance's API for {@code Wallet}: the balance and movement queries, and the
 * one write an investor can ask for directly — a debit.
 *
 * <p>The controller holds no business rules. It validates the request format,
 * delegates to {@link WalletCommandService}/{@link WalletQueryService}, and
 * translates the answer back — same rule as {@code DepositController}. Errors
 * are not mapped here: it throws, and {@code FinanceExceptionHandler} renders
 * the problem+json.
 *
 * <p>Every route is under {@code /accounts/{accountId}}, so every route is
 * guarded by {@code CallerOwnershipInterceptor}: the {@code X-User-Id} the
 * gateway injected must equal {@code {accountId}} or the answer is
 * {@code 403} before this class runs.
 *
 * <p>A wallet that was never opened (the investor has not deposited in that
 * currency yet — {@code Wallet} is created lazily) is not an error for the
 * collection: {@code listWalletMovements} answers {@code 200} with an empty
 * page, symmetric with how {@code listDeposits} never 404s. It is a
 * {@code 404 wallet-not-found} for the two routes that address the one
 * resource, {@code getWalletBalance} and {@code debitWallet} — there is no
 * balance to show and nothing to debit.
 */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Wallets", description = "Query an investor's wallet balance and movement history, and debit it.")
public class WalletController {

    private final WalletCommandService walletCommandService;
    private final WalletQueryService walletQueryService;

    public WalletController(WalletCommandService walletCommandService, WalletQueryService walletQueryService) {
        this.walletCommandService = walletCommandService;
        this.walletQueryService = walletQueryService;
    }

    @PostMapping(value = "/accounts/{accountId}/wallets/{currency}/debits", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Debit a wallet",
            description = "Applies the debit synchronously and answers with its debitId — the value the client "
                    + "hands to Investment as transactionId. Repeating the same Idempotency-Key with the same body "
                    + "answers the same debitId without debiting again.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Debited — or replayed, if Idempotency-Key repeats"),
            @ApiResponse(responseCode = "400", description = "Malformed request: accountId, currency, reason, amount, or missing Idempotency-Key"),
            @ApiResponse(responseCode = "403", description = "X-User-Id does not match accountId"),
            @ApiResponse(responseCode = "404", description = "No wallet yet for this account in this currency"),
            @ApiResponse(responseCode = "409", description = "insufficient-balance, or Idempotency-Key reused with different content")
    })
    public ResponseEntity<WalletDebitResource> debitWallet(
            @PathVariable String accountId,
            @PathVariable String currency,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateWalletDebitResource resource) {
        AccountId parsedAccountId = AccountId.of(accountId);
        Currency parsedCurrency = Currency.fromIsoCode(currency);
        DebitWalletCommand command =
                DebitWalletCommandFromResourceAssembler.toCommandFromResource(parsedAccountId, parsedCurrency, resource);

        // Either the id this request minted (first time) or the one the same
        // key was answered with before (replay) — the body is built from the
        // command's own inputs either way, never from the Read Model.
        DebitId resolvedId = walletCommandService.handle(command, parsedAccountId, new IdempotencyKey(idempotencyKey));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DebitWalletCommandFromResourceAssembler.toResourceFrom(resolvedId, parsedAccountId, command));
    }

    @GetMapping("/accounts/{accountId}/wallets/{currency}")
    @Operation(summary = "Get a wallet's balance")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "400", description = "accountId or currency is not valid"),
            @ApiResponse(responseCode = "403", description = "X-User-Id does not match accountId"),
            @ApiResponse(responseCode = "404", description = "No wallet yet for this account in this currency")
    })
    public ResponseEntity<WalletResource> getWalletBalance(@PathVariable String accountId,
                                                            @PathVariable String currency) {
        AccountId parsedAccountId = AccountId.of(accountId);
        Currency parsedCurrency = Currency.fromIsoCode(currency);

        return walletQueryService.getWalletBalance(new GetWalletBalanceQuery(parsedAccountId, parsedCurrency))
                .map(balance -> ResponseEntity.ok(WalletResourceFromBalanceAssembler.toResourceFromBalance(balance)))
                .orElseThrow(() -> new WalletNotFoundException(
                        WalletId.derive(parsedAccountId, parsedCurrency).toString()));
    }

    @GetMapping("/accounts/{accountId}/wallets/{currency}/movements")
    @Operation(summary = "List a wallet's movement history, paginated")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page, possibly empty"),
            @ApiResponse(responseCode = "400", description = "accountId/currency is not valid, or page/size is invalid"),
            @ApiResponse(responseCode = "403", description = "X-User-Id does not match accountId")
    })
    public ResponseEntity<WalletMovementPageResource> listWalletMovements(
            @PathVariable String accountId,
            @PathVariable String currency,
            @Parameter(description = "0-based") @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AccountId parsedAccountId = AccountId.of(accountId);
        Currency parsedCurrency = Currency.fromIsoCode(currency);
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException("page must be >= 0 and size must be > 0");
        }

        WalletMovementPage result = walletQueryService.listWalletMovements(
                new ListWalletMovementsQuery(parsedAccountId, parsedCurrency, page, size));
        return ResponseEntity.ok(WalletMovementPageResourceFromPageAssembler.toResourceFromPage(result));
    }
}
