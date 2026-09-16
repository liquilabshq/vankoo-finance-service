package com.liquilabs.vankoo.finance.interfaces.rest.problems;

import com.liquilabs.vankoo.finance.domain.exceptions.CommandRejectedException;
import com.liquilabs.vankoo.finance.domain.exceptions.DepositNotFoundException;
import com.liquilabs.vankoo.finance.domain.exceptions.IdempotencyKeyConflictException;
import com.liquilabs.vankoo.finance.domain.exceptions.InsufficientBalanceException;
import com.liquilabs.vankoo.finance.domain.exceptions.InvalidDebitAmountException;
import com.liquilabs.vankoo.finance.domain.exceptions.InvalidDepositAmountException;
import com.liquilabs.vankoo.finance.domain.exceptions.UnsupportedCurrencyException;
import com.liquilabs.vankoo.finance.domain.exceptions.WalletNotFoundException;
import com.liquilabs.vankoo.finance.interfaces.rest.interceptors.CallerMismatchException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one place Finance turns an exception into an HTTP error, as
 * {@code application/problem+json} with a {@code code} (see
 * {@link FinanceProblem}).
 *
 * <p>Before this class every controller mapped its errors inline and with an
 * empty body, because the platform-wide error format was still an open
 * decision in the contract. It is not any more: the gateway and IAM already
 * answer RFC 9457 with {@code code}, and the mobile app parses it. Controllers
 * now just throw (or let Spring throw) and this advice answers — the statuses
 * they used to return are unchanged, they only gained a body.
 *
 * <p>Domain exceptions that an aggregate throws are listed here too even though
 * they normally arrive as {@link CommandRejectedException} (the class is lost
 * crossing Axon Server — see {@code CommandRejectionHandlerInterceptor}): the
 * direct mapping costs nothing and keeps the advice honest if a service ever
 * validates before dispatching.
 *
 * <p>Unlisted exceptions keep Spring Boot's default handling. Only
 * {@code DepositNotFoundException} is new here; the query side still answers a
 * missing deposit through {@code Optional}, so it exists for the controller
 * to throw rather than build a 404 by hand.
 */
@RestControllerAdvice
public class FinanceExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinanceExceptionHandler.class);

    @ExceptionHandler(CommandRejectedException.class)
    public ResponseEntity<ProblemDetail> onCommandRejected(CommandRejectedException exception,
                                                           HttpServletRequest request) {
        FinanceProblem problem = FinanceProblem.fromCode(exception.getCode())
                .orElseGet(() -> {
                    // A code this advice does not know is a wiring mistake between the
                    // interceptor and this enum, not something to hide behind a 500.
                    LOGGER.error("Command rejected with unmapped code {}: {}", exception.getCode(),
                            exception.getMessage());
                    return FinanceProblem.INVALID_REQUEST;
                });
        return respond(problem, request, exception.getMessage());
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ProblemDetail> onInsufficientBalance(InsufficientBalanceException exception,
                                                               HttpServletRequest request) {
        return respond(FinanceProblem.INSUFFICIENT_BALANCE, request, exception.getMessage());
    }

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ProblemDetail> onWalletNotFound(WalletNotFoundException exception,
                                                          HttpServletRequest request) {
        return respond(FinanceProblem.WALLET_NOT_FOUND, request, exception.getMessage());
    }

    @ExceptionHandler(DepositNotFoundException.class)
    public ResponseEntity<ProblemDetail> onDepositNotFound(DepositNotFoundException exception,
                                                           HttpServletRequest request) {
        return respond(FinanceProblem.DEPOSIT_NOT_FOUND, request, exception.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ProblemDetail> onIdempotencyKeyConflict(IdempotencyKeyConflictException exception,
                                                                  HttpServletRequest request) {
        return respond(FinanceProblem.IDEMPOTENCY_KEY_CONFLICT, request, exception.getMessage());
    }

    @ExceptionHandler(CallerMismatchException.class)
    public ResponseEntity<ProblemDetail> onCallerMismatch(CallerMismatchException exception,
                                                          HttpServletRequest request) {
        // The message names the caller and the account; keep it in the log,
        // not in the body — the client learns nothing beyond "not yours".
        LOGGER.warn("{} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        return respond(FinanceProblem.FORBIDDEN, request, null);
    }

    /**
     * Malformed identifiers, unknown enum names, unsupported currencies,
     * amounts the aggregate refuses, a body that is not JSON, and a missing
     * {@code Idempotency-Key} all mean the same thing to a client: fix the
     * request. One code for all of them, with the specific reason in
     * {@code detail}.
     */
    @ExceptionHandler({
            IllegalArgumentException.class,
            UnsupportedCurrencyException.class,
            InvalidDebitAmountException.class,
            InvalidDepositAmountException.class,
            MissingRequestHeaderException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> onInvalidRequest(Exception exception, HttpServletRequest request) {
        return respond(FinanceProblem.INVALID_REQUEST, request, exception.getMessage());
    }

    /**
     * Bean Validation failures on a {@code @Valid} body, with the
     * {@code errors[{field, code, message}]} extension IAM uses for the same
     * case, so the app can point at the field.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> onValidationFailed(MethodArgumentNotValidException exception,
                                                            HttpServletRequest request) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(FinanceExceptionHandler::toViolation)
                .toList();
        ProblemDetail detail = FinanceProblem.VALIDATION_FAILED.toProblemDetail(instanceOf(request));
        detail.setProperty("errors", errors);
        return ResponseEntity.status(FinanceProblem.VALIDATION_FAILED.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(detail);
    }

    private static ResponseEntity<ProblemDetail> respond(FinanceProblem problem,
                                                         HttpServletRequest request,
                                                         String occurrenceDetail) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem.toProblemDetail(instanceOf(request), occurrenceDetail));
    }

    private static URI instanceOf(HttpServletRequest request) {
        return URI.create(request.getRequestURI());
    }

    private static Map<String, String> toViolation(FieldError error) {
        Map<String, String> violation = new LinkedHashMap<>();
        violation.put("field", error.getField());
        violation.put("code", error.getCode());
        violation.put("message", error.getDefaultMessage());
        return violation;
    }
}
