package com.liquilabs.vankoo.finance.interfaces.rest.problems;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

/**
 * Every failure Finance's REST API reports, in the shape RFC 9457 asks for
 * and with the one extension member Vankoo clients switch on: {@code code}.
 *
 * <p>Twin of {@code GatewayProblem} in vankoo-api-gateway (and of
 * {@code ApiProblem} in IAM): same {@code type} prefix, same kebab-case codes,
 * so the mobile app's {@code ProblemDetailDto} reads a Finance error exactly
 * like it reads a gateway {@code 401}. Only the {@code code} is contractual;
 * {@code title}/{@code detail} are for humans and may change.
 *
 * <p>Statuses are chosen so that two different problems may share one status
 * — {@code insufficient-balance} and {@code idempotency-key-conflict} are both
 * {@code 409} — which is exactly why clients must branch on {@code code} and
 * never on the status alone.
 */
public enum FinanceProblem {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request",
            "The request could not be understood."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed",
            "One or more fields are invalid."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "forbidden", "Forbidden",
            "The authenticated user does not own this account."),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "wallet-not-found", "Wallet not found",
            "No wallet has been opened for this account in this currency."),
    DEPOSIT_NOT_FOUND(HttpStatus.NOT_FOUND, "deposit-not-found", "Deposit not found",
            "No deposit exists with that identifier."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "idempotency-key-conflict", "Idempotency-Key conflict",
            "This Idempotency-Key was already used with different request content."),
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "insufficient-balance", "Insufficient balance",
            "The wallet's balance does not cover this debit.");

    /** Prefix of the {@code type} URI, the same one every Vankoo service uses. */
    public static final String TYPE_BASE = "https://docs.vankoo.dev/errors/";

    /** Name of the extension member clients switch on. */
    public static final String CODE_PROPERTY = "code";

    private final HttpStatus status;
    private final String code;
    private final String title;
    private final String detail;

    FinanceProblem(HttpStatus status, String code, String title, String detail) {
        this.status = status;
        this.code = code;
        this.title = title;
        this.detail = detail;
    }

    /** Looks a problem up by its wire {@code code}, e.g. one carried back from a rejected command. */
    public static Optional<FinanceProblem> fromCode(String code) {
        return Arrays.stream(values()).filter(problem -> problem.code.equals(code)).findFirst();
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public ProblemDetail toProblemDetail(URI instance) {
        return toProblemDetail(instance, detail);
    }

    /** Same problem, with a {@code detail} specific to this occurrence (never the default one). */
    public ProblemDetail toProblemDetail(URI instance, String occurrenceDetail) {
        var problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setType(URI.create(TYPE_BASE + code));
        problemDetail.setTitle(title);
        problemDetail.setDetail(occurrenceDetail == null || occurrenceDetail.isBlank() ? detail : occurrenceDetail);
        problemDetail.setInstance(instance);
        problemDetail.setProperty(CODE_PROPERTY, code);
        return problemDetail;
    }
}
