package com.liquilabs.vankoo.finance.infrastructure.eventstore.axon;

/**
 * The part of a rejected command that is allowed to cross Axon Server.
 *
 * <p>Attached as the {@code details} of a
 * {@code org.axonframework.commandhandling.CommandExecutionException} by
 * {@link CommandRejectionHandlerInterceptor}. Axon serializes {@code details}
 * with the general serializer (Jackson here) and hands it back to the
 * dispatcher, which is the only thing that survives the trip — the original
 * exception class does not. Kept to two strings on purpose so it never needs
 * a custom serializer and never leaks internals.
 *
 * @param code    stable, kebab-case, the value the REST layer maps to a
 *                problem+json {@code code}
 * @param message the handler's own message, for logs and {@code detail}
 */
public record CommandRejection(String code, String message) {

    public static final String INSUFFICIENT_BALANCE = "insufficient-balance";
    public static final String WALLET_NOT_FOUND = "wallet-not-found";
    public static final String INVALID_REQUEST = "invalid-request";
}
