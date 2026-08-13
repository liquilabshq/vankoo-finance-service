package com.liquilabs.vankoo.finance.interfaces.rest.webhooks;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Reduces a raw webhook body to something safe to keep.
 *
 * <p>The inbox stores this digest instead of the payload: correlating a row with
 * a delivery in the provider's dashboard only needs a fingerprint, and the
 * normalized fields are all a retry needs. Nothing of the provider's body is
 * retained, so there is no retention policy to define for it.
 */
final class PayloadDigest {

    private PayloadDigest() {
    }

    static String sha256Hex(String rawPayload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is required of every JVM; if it is missing, nothing else here is trustworthy either.
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
