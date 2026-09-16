package com.liquilabs.vankoo.finance.interfaces.rest.interceptors;

/**
 * The caller the gateway authenticated ({@code X-User-Id}) is not the owner of
 * the {@code {accountId}} the request addresses — or there is no caller at
 * all. Thrown by {@link CallerOwnershipInterceptor} and answered as
 * {@code 403 forbidden} by {@code FinanceExceptionHandler}.
 */
public class CallerMismatchException extends RuntimeException {

    public CallerMismatchException(String message) {
        super(message);
    }
}
