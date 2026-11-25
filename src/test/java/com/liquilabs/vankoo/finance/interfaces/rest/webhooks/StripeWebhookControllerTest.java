package com.liquilabs.vankoo.finance.interfaces.rest.webhooks;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.NormalizedDepositStatus;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.VerifiedProviderDepositUpdate;
import com.liquilabs.vankoo.finance.domain.services.WebhookInboxService;
import com.liquilabs.vankoo.finance.domain.services.WebhookInboxService.InboxAdmission;
import com.liquilabs.vankoo.finance.infrastructure.providers.stripe.StripePaymentProvider;
import com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration.StripePaymentProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.liquilabs.vankoo.finance.infrastructure.providers.stripe.StripeWebhookFixtures.WEBHOOK_SECRET;
import static com.liquilabs.vankoo.finance.infrastructure.providers.stripe.StripeWebhookFixtures.checkoutEvent;
import static com.liquilabs.vankoo.finance.infrastructure.providers.stripe.StripeWebhookFixtures.paymentIntentEvent;
import static com.liquilabs.vankoo.finance.infrastructure.providers.stripe.StripeWebhookFixtures.sign;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba del endpoint de webhooks de Stripe de extremo a extremo dentro del
 * servicio: petición HTTP firmada, verificación real con el adaptador de Stripe y
 * admisión en el inbox (simulado con Mockito para no levantar Postgres ni Axon).
 *
 * @author LiquiLabs
 */
@WebMvcTest(StripeWebhookController.class)
@Import({StripePaymentProvider.class, StripePaymentProperties.class})
@TestPropertySource(properties = "stripe.webhook-secret=" + WEBHOOK_SECRET)
class StripeWebhookControllerTest {

    private static final String WEBHOOK_URL = "/api/v1/payment-providers/stripe/webhooks";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookInboxService webhookInboxService;

    @Test
    void aSignedEventIsRecordedInTheInboxAndAcknowledged() throws Exception {
        String payload = checkoutEvent("evt_test_c1", "checkout.session.completed", "cs_test_c1", "paid");
        when(webhookInboxService.accept(any(), anyString())).thenReturn(InboxAdmission.ACCEPTED);

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType("application/json")
                        .header("Stripe-Signature", sign(payload, WEBHOOK_SECRET))
                        .content(payload))
                .andExpect(status().isOk());

        var update = ArgumentCaptor.forClass(VerifiedProviderDepositUpdate.class);
        var digest = ArgumentCaptor.forClass(String.class);
        verify(webhookInboxService).accept(update.capture(), digest.capture());
        assertThat(update.getValue().providerEventId().value()).isEqualTo("evt_test_c1");
        assertThat(update.getValue().status()).isEqualTo(NormalizedDepositStatus.SUCCEEDED);
        // Al inbox llega solo el SHA-256 del cuerpo, nunca el cuerpo.
        assertThat(digest.getValue()).isEqualTo(PayloadDigest.sha256Hex(payload)).hasSize(64);
    }

    @Test
    void aRepeatedDeliveryIsStillA200() throws Exception {
        String payload = checkoutEvent("evt_test_c2", "checkout.session.completed", "cs_test_c2", "paid");
        when(webhookInboxService.accept(any(), anyString())).thenReturn(InboxAdmission.DUPLICATE);

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType("application/json")
                        .header("Stripe-Signature", sign(payload, WEBHOOK_SECRET))
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void aForgedSignatureIsA400AndNothingReachesTheInbox() throws Exception {
        String payload = checkoutEvent("evt_test_c3", "checkout.session.completed", "cs_test_c3", "paid");

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType("application/json")
                        .header("Stripe-Signature", sign(payload, "whsec_attacker"))
                        .content(payload))
                .andExpect(status().isBadRequest());

        verify(webhookInboxService, never()).accept(any(), anyString());
    }

    @Test
    void aMissingSignatureHeaderIsA400() throws Exception {
        String payload = checkoutEvent("evt_test_c4", "checkout.session.completed", "cs_test_c4", "paid");

        mockMvc.perform(post(WEBHOOK_URL).contentType("application/json").content(payload))
                .andExpect(status().isBadRequest());

        verify(webhookInboxService, never()).accept(any(), anyString());
    }

    @Test
    void anUnsupportedEventTypeIsAcknowledgedSoStripeStopsRetrying() throws Exception {
        String payload = paymentIntentEvent("evt_test_c5");

        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType("application/json")
                        .header("Stripe-Signature", sign(payload, WEBHOOK_SECRET))
                        .content(payload))
                .andExpect(status().isOk());

        verify(webhookInboxService, never()).accept(any(), anyString());
    }
}
