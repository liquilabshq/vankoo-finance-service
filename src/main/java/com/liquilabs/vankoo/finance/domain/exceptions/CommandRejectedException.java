package com.liquilabs.vankoo.finance.domain.exceptions;

/**
 * A command was refused by its handler, and only the reason's {@code code}
 * survived the trip back.
 *
 * <p>Commands cross Axon Server on their way to the aggregate, and the
 * exception the handler threw does not come back as itself: it arrives as a
 * {@code CommandExecutionException} carrying whatever <em>details</em> the
 * handler side attached. {@code CommandRejectionHandlerInterceptor} attaches a
 * {@code code} for each domain exception it knows, and the application layer
 * rethrows it as this — one exception, keyed by {@code code}, that the REST
 * layer can map to a problem+json response without ever seeing the original
 * class.
 */
public class CommandRejectedException extends RuntimeException {

    private final String code;

    public CommandRejectedException(String code, String message) {
        super(message);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
