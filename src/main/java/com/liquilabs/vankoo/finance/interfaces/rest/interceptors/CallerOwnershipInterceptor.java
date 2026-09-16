package com.liquilabs.vankoo.finance.interfaces.rest.interceptors;

import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.UUID;

/**
 * Makes every route under {@code /api/v1/accounts/{accountId}/**} answer only
 * to the account's owner.
 *
 * <p>The gateway validates the JWT and injects {@code X-User-Id} — the token's
 * {@code sub}, which is the same UUID the mobile app uses as {@code accountId}
 * — overwriting any value the client sent. Until now Finance never read it,
 * so any authenticated user could read or (from this card on) debit anyone
 * else's wallet just by changing the id in the path. This closes that: the
 * header must be present, be a UUID, and equal {@code {accountId}}; anything
 * else is {@code 403 forbidden}, for reads as much as for writes.
 *
 * <p>Two things it deliberately does <em>not</em> do:
 * <ul>
 *   <li>Decide on a malformed {@code {accountId}}. That is a format error the
 *       controller already answers with {@code 400}; ownership is only
 *       meaningful once the path names a real account.</li>
 *   <li>Distinguish "no header" from "wrong header". A request that reaches
 *       Finance without {@code X-User-Id} did not come through the gateway
 *       (or came through a route that does not authenticate), and there is no
 *       identity to prove ownership with — same answer.</li>
 * </ul>
 *
 * <p>Registered on {@code /api/v1/accounts/**} by
 * {@code WebMvcConfiguration}; the path variable is read from what the
 * handler mapping already extracted, so the pattern is spelled out once, in
 * the controllers.
 */
public class CallerOwnershipInterceptor implements HandlerInterceptor {

    public static final String CALLER_HEADER = "X-User-Id";
    static final String ACCOUNT_ID_VARIABLE = "accountId";

    @Override
    public boolean preHandle(@Nonnull HttpServletRequest request,
                             @Nonnull HttpServletResponse response,
                             @Nonnull Object handler) {
        UUID accountId = pathAccountId(request);
        if (accountId == null) {
            return true;
        }

        UUID caller = parse(request.getHeader(CALLER_HEADER));
        if (caller == null) {
            throw new CallerMismatchException("No " + CALLER_HEADER + " identifies the caller");
        }
        if (!caller.equals(accountId)) {
            throw new CallerMismatchException("Caller " + caller + " does not own account " + accountId);
        }
        return true;
    }

    private static UUID pathAccountId(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attribute instanceof Map<?, ?> variables)) {
            return null;
        }
        Object raw = variables.get(ACCOUNT_ID_VARIABLE);
        return raw instanceof String value ? parse(value) : null;
    }

    private static UUID parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
