package com.liquilabs.vankoo.finance.interfaces.rest.controllers;

import com.liquilabs.vankoo.finance.domain.exceptions.UnsupportedCurrencyException;
import com.liquilabs.vankoo.finance.domain.model.queries.GetWalletBalanceQuery;
import com.liquilabs.vankoo.finance.domain.model.queries.ListWalletMovementsQuery;
import com.liquilabs.vankoo.finance.domain.model.queries.WalletMovementPage;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.services.WalletQueryService;
import com.liquilabs.vankoo.finance.interfaces.rest.resources.WalletMovementPageResource;
import com.liquilabs.vankoo.finance.interfaces.rest.resources.WalletResource;
import com.liquilabs.vankoo.finance.interfaces.rest.transform.WalletMovementPageResourceFromPageAssembler;
import com.liquilabs.vankoo.finance.interfaces.rest.transform.WalletResourceFromBalanceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Finance's read-only API for {@code Wallet} — no command service here: this
 * card only queries, it never dispatches a wallet command.
 *
 * <p>The controller holds no business rules. It validates the request format,
 * delegates to {@link WalletQueryService}, and translates the answer back —
 * same rule as {@code DepositController}.
 *
 * <p>A wallet that was never opened (the investor has not deposited in that
 * currency yet — {@code Wallet} is created lazily) is not an error:
 * {@code getWalletBalance} answers {@code 404} for it (it addresses one
 * resource), but {@code listWalletMovements} answers {@code 200} with an
 * empty page (it addresses a collection) — symmetric with how
 * {@code listDeposits} never 404s for an account with zero deposits.
 */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Wallets", description = "Query an investor's wallet balance and movement history.")
public class WalletController {

    private final WalletQueryService walletQueryService;

    public WalletController(WalletQueryService walletQueryService) {
        this.walletQueryService = walletQueryService;
    }

    @GetMapping("/accounts/{accountId}/wallets/{currency}")
    @Operation(summary = "Get a wallet's balance")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "400", description = "accountId or currency is not valid"),
            @ApiResponse(responseCode = "404", description = "No wallet yet for this account in this currency")
    })
    public ResponseEntity<WalletResource> getWalletBalance(@PathVariable String accountId,
                                                            @PathVariable String currency) {
        AccountId parsedAccountId;
        Currency parsedCurrency;
        try {
            parsedAccountId = AccountId.of(accountId);
            parsedCurrency = Currency.fromIsoCode(currency);
        } catch (IllegalArgumentException | UnsupportedCurrencyException exception) {
            return ResponseEntity.badRequest().build();
        }

        return walletQueryService.getWalletBalance(new GetWalletBalanceQuery(parsedAccountId, parsedCurrency))
                .map(balance -> ResponseEntity.ok(WalletResourceFromBalanceAssembler.toResourceFromBalance(balance)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/accounts/{accountId}/wallets/{currency}/movements")
    @Operation(summary = "List a wallet's movement history, paginated")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page, possibly empty"),
            @ApiResponse(responseCode = "400", description = "accountId/currency is not valid, or page/size is invalid")
    })
    public ResponseEntity<WalletMovementPageResource> listWalletMovements(
            @PathVariable String accountId,
            @PathVariable String currency,
            @Parameter(description = "0-based") @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AccountId parsedAccountId;
        Currency parsedCurrency;
        try {
            parsedAccountId = AccountId.of(accountId);
            parsedCurrency = Currency.fromIsoCode(currency);
        } catch (IllegalArgumentException | UnsupportedCurrencyException exception) {
            return ResponseEntity.badRequest().build();
        }
        if (page < 0 || size <= 0) {
            return ResponseEntity.badRequest().build();
        }

        WalletMovementPage result = walletQueryService.listWalletMovements(
                new ListWalletMovementsQuery(parsedAccountId, parsedCurrency, page, size));
        return ResponseEntity.ok(WalletMovementPageResourceFromPageAssembler.toResourceFromPage(result));
    }
}
