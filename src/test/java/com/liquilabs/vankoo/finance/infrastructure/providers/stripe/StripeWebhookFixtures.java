package com.liquilabs.vankoo.finance.infrastructure.providers.stripe;

import com.stripe.Stripe;
import com.stripe.net.Webhook;

/**
 * Eventos de webhook de Stripe y su firma {@code Stripe-Signature}, generados
 * igual que los firma Stripe: HMAC-SHA256 de {@code "<timestamp>.<payload>"} con
 * el secreto del endpoint.
 *
 * @author LiquiLabs
 */
public final class StripeWebhookFixtures {

    public static final String WEBHOOK_SECRET = "whsec_test_vankoo";

    private StripeWebhookFixtures() {
    }

    public static String checkoutEvent(String eventId, String type, String sessionId, String paymentStatus) {
        return checkoutEvent(eventId, type, sessionId, paymentStatus, Stripe.API_VERSION);
    }

    public static String checkoutEvent(String eventId, String type, String sessionId,
                                       String paymentStatus, String apiVersion) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "%s",
                  "created": 1764000000,
                  "livemode": false,
                  "type": "%s",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "checkout.session",
                      "status": "complete",
                      "payment_status": "%s",
                      "client_reference_id": "0199a3e4-7b1c-7d2e-8f3a-1b2c3d4e5f60",
                      "metadata": {"deposit_id": "0199a3e4-7b1c-7d2e-8f3a-1b2c3d4e5f60"}
                    }
                  }
                }
                """.formatted(eventId, apiVersion, type, sessionId, paymentStatus);
    }

    public static String paymentIntentEvent(String eventId) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "%s",
                  "created": 1764000000,
                  "livemode": false,
                  "type": "payment_intent.succeeded",
                  "data": {"object": {"id": "pi_test_1", "object": "payment_intent"}}
                }
                """.formatted(eventId, Stripe.API_VERSION);
    }

    public static String sign(String payload, String secret) {
        return sign(payload, secret, Webhook.Util.getTimeNow());
    }

    public static String sign(String payload, String secret, long timestampSeconds) {
        try {
            String signature = Webhook.Util.computeHmacSha256(secret, timestampSeconds + "." + payload);
            return "t=" + timestampSeconds + ",v1=" + signature;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign the test payload", exception);
        }
    }
}
