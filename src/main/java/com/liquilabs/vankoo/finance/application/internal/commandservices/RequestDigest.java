package com.liquilabs.vankoo.finance.application.internal.commandservices;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Reduces a client request's significant fields to a fingerprint, so a
 * repeated {@code Idempotency-Key} can be told apart from one reused with
 * different content.
 *
 * <p>Same algorithm as {@code interfaces.rest.webhooks.PayloadDigest}, but not
 * shared with it: that class hashes a raw webhook body, this one hashes a
 * canonical string built from command fields, and it is package-private over
 * there — importing it here would also point a dependency from
 * {@code application} back at {@code interfaces}, the wrong direction.
 */
final class RequestDigest {

    private RequestDigest() {
    }

    static String sha256Hex(String canonicalRequest) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is required of every JVM; if it is missing, nothing else here is trustworthy either.
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
