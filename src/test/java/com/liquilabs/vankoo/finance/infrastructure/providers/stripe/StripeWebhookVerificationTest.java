package com.liquilabs.vankoo.finance.infrastructure.providers.stripe;

import com.liquilabs.vankoo.finance.domain.exceptions.InvalidWebhookSignatureException;
import com.liquilabs.vankoo.finance.domain.exceptions.PaymentProviderRejectedException;
import com.liquilabs.vankoo.finance.domain.exceptions.UnsupportedProviderEventException;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.FailureReason;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.NormalizedDepositStatus;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.VerifiedProviderDepositUpdate;
import com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration.StripePaymentProperties;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static com.liquilabs.vankoo.finance.infrastructure.providers.stripe.StripeWebhookFixtures.WEBHOOK_SECRET;
import static com.liquilabs.vankoo.finance.infrastructure.providers.stripe.StripeWebhookFixtures.checkoutEvent;
import static com.liquilabs.vankoo.finance.infrastructure.providers.stripe.StripeWebhookFixtures.paymentIntentEvent;
import static com.liquilabs.vankoo.finance.infrastructure.providers.stripe.StripeWebhookFixtures.sign;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verificación de webhooks de Stripe con eventos firmados como los firma Stripe.
 *
 * <p>Se ejercita la verificación real del SDK ({@code Webhook.constructEvent}):
 * firma válida, payload alterado, secreto equivocado, firma vencida (ataque de
 * repetición) y tipos de evento no soportados.
 *
 * @author LiquiLabs
 */
class StripeWebhookVerificationTest {

    private StripePaymentProvider provider;

    @BeforeEach
    void setUp() {
        var properties = new StripePaymentProperties();
        properties.setWebhookSecret(WEBHOOK_SECRET);
        provider = new StripePaymentProvider(properties);
    }

    @ParameterizedTest(name = "{0} (payment_status={1}) -> {2}")
    @CsvSource({
            "checkout.session.completed,               paid,   SUCCEEDED",
            "checkout.session.completed,               unpaid, PROCESSING",
            "checkout.session.async_payment_succeeded, paid,   SUCCEEDED",
            "checkout.session.async_payment_failed,    unpaid, FAILED",
            "checkout.session.expired,                 unpaid, CANCELLED"
    })
    void aValidlySignedEventIsNormalized(String type, String paymentStatus, NormalizedDepositStatus expected) {
        String payload = checkoutEvent("evt_test_1", type, "cs_test_w1", paymentStatus);

        VerifiedProviderDepositUpdate update = provider.verifyWebhook(payload, sign(payload, WEBHOOK_SECRET));

        assertThat(update.provider()).isEqualTo(Provider.STRIPE);
        assertThat(update.providerDepositId().value()).isEqualTo("cs_test_w1");
        assertThat(update.providerEventId().value()).isEqualTo("evt_test_1");
        assertThat(update.status()).isEqualTo(expected);
        assertThat(update.observedAt()).isEqualTo(Instant.ofEpochSecond(1764000000));
        assertThat(update.failureReason())
                .isEqualTo(expected == NormalizedDepositStatus.FAILED ? FailureReason.UNKNOWN : null);
        assertThat(update.cancellationReason())
                .isEqualTo(expected == NormalizedDepositStatus.CANCELLED ? "EXPIRED" : null);
    }

    @Test
    void anEventFromAnotherApiVersionIsStillRead() {
        // Si la cuenta usa otra versión de la API que el SDK, getObject() viene vacío
        // y el adaptador debe recurrir a deserializeUnsafe() en vez de descartarlo.
        String payload = checkoutEvent("evt_test_2", "checkout.session.completed", "cs_test_w2", "paid", "2020-08-27");

        var update = provider.verifyWebhook(payload, sign(payload, WEBHOOK_SECRET));

        assertThat(update.status()).isEqualTo(NormalizedDepositStatus.SUCCEEDED);
    }

    @Test
    void aTamperedPayloadIsRejected() {
        String original = checkoutEvent("evt_test_3", "checkout.session.completed", "cs_test_w3", "unpaid");
        String signature = sign(original, WEBHOOK_SECRET);
        String tampered = original.replace("\"unpaid\"", "\"paid\"");

        assertThatThrownBy(() -> provider.verifyWebhook(tampered, signature))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void aSignatureMadeWithAnotherSecretIsRejected() {
        String payload = checkoutEvent("evt_test_4", "checkout.session.completed", "cs_test_w4", "paid");

        assertThatThrownBy(() -> provider.verifyWebhook(payload, sign(payload, "whsec_attacker")))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void anOldSignatureIsRejectedToPreventReplay() {
        String payload = checkoutEvent("evt_test_5", "checkout.session.completed", "cs_test_w5", "paid");
        long elevenMinutesAgo = Webhook.Util.getTimeNow() - 660;

        // La tolerancia por defecto del SDK es de 300 s.
        assertThatThrownBy(() -> provider.verifyWebhook(payload, sign(payload, WEBHOOK_SECRET, elevenMinutesAgo)))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void aMissingOrGarbageSignatureHeaderIsRejected() {
        String payload = checkoutEvent("evt_test_6", "checkout.session.completed", "cs_test_w6", "paid");

        assertThatThrownBy(() -> provider.verifyWebhook(payload, "not-a-signature"))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void anAuthenticEventOfAnUnsupportedTypeIsReportedAsSuch() {
        String payload = paymentIntentEvent("evt_test_7");

        assertThatThrownBy(() -> provider.verifyWebhook(payload, sign(payload, WEBHOOK_SECRET)))
                .isInstanceOf(UnsupportedProviderEventException.class)
                .hasMessageContaining("payment_intent.succeeded");
    }

    @Test
    void aSignedBodyThatIsNotJsonIsRejectedAsUnreadable() {
        String payload = "this is not json";

        assertThatThrownBy(() -> provider.verifyWebhook(payload, sign(payload, WEBHOOK_SECRET)))
                .isInstanceOf(PaymentProviderRejectedException.class);
    }

    @Test
    void withoutAWebhookSecretNothingIsVerified() {
        var unconfigured = new StripePaymentProvider(new StripePaymentProperties());
        String payload = checkoutEvent("evt_test_8", "checkout.session.completed", "cs_test_w8", "paid");

        assertThatThrownBy(() -> unconfigured.verifyWebhook(payload, sign(payload, WEBHOOK_SECRET)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stripe.webhook-secret");
    }
}
