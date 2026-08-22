package com.liquilabs.vankoo.finance.interfaces.rest.webhooks;

import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.PaymentProvider;
import com.liquilabs.vankoo.finance.domain.exceptions.InvalidWebhookSignatureException;
import com.liquilabs.vankoo.finance.domain.exceptions.PaymentProviderRejectedException;
import com.liquilabs.vankoo.finance.domain.exceptions.UnsupportedProviderEventException;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.VerifiedProviderDepositUpdate;
import com.liquilabs.vankoo.finance.domain.services.WebhookInboxService;
import com.liquilabs.vankoo.finance.domain.services.WebhookInboxService.InboxAdmission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Stripe's callbacks.
 *
 * <p>It is under {@code rest} because it is HTTP, and in its own subpackage
 * because it is not our API: it takes a raw body, verifies a signature, and
 * answers without waiting for anything to be processed.
 *
 * <p>Three things this method must keep doing:
 *
 * <ol>
 *   <li>Take the body as a {@code String}. Letting Jackson parse it first would
 *       verify the signature over bytes we re-serialized, not the ones Stripe
 *       signed.</li>
 *   <li>Store nothing until the signature validates. An unverified payload is
 *       not evidence of anything.</li>
 *   <li>Answer as soon as the event is in the inbox. Stripe retries what it
 *       considers slow, so waiting for the command would create duplicates.</li>
 * </ol>
 *
 * <p>The raw payload does not leave this method: only its digest crosses into
 * {@code application}.
 */
@RestController
@RequestMapping(value = "/v1/payment-providers/stripe/webhooks", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Provider Webhooks", description = "Callbacks from payment providers. Not part of the Vankoo API.")
public class StripeWebhookController {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeWebhookController.class);

    private final PaymentProvider paymentProvider;
    private final WebhookInboxService webhookInboxService;

    public StripeWebhookController(PaymentProvider paymentProvider, WebhookInboxService webhookInboxService) {
        this.paymentProvider = paymentProvider;
        this.webhookInboxService = webhookInboxService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Receive a Stripe webhook",
            description = "Verifies the signature and records the event in the inbox. The response does not "
                    + "wait for the deposit to be updated.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event recorded, already known, or not of interest"),
            @ApiResponse(responseCode = "400", description = "Signature does not validate, or body is not an event")
    })
    public ResponseEntity<Void> handle(@RequestBody String rawPayload,
                                       @RequestHeader("Stripe-Signature") String signature) {
        VerifiedProviderDepositUpdate update;
        try {
            update = paymentProvider.verifyWebhook(rawPayload, signature);
        } catch (InvalidWebhookSignatureException exception) {
            // Nothing is stored, and nothing is logged from the body: an
            // unverified payload must not reach the inbox in any form.
            LOGGER.warn("Rejecting webhook with an invalid signature");
            return ResponseEntity.badRequest().build();
        } catch (UnsupportedProviderEventException exception) {
            // Authentic, but a type we do not normalize. Acknowledged on purpose:
            // an error here would only make Stripe retry an event we will never
            // apply. The fix is to narrow the subscription in the dashboard.
            LOGGER.warn("Acknowledging a Stripe event we do not handle: {}", exception.getMessage());
            return ResponseEntity.ok().build();
        } catch (PaymentProviderRejectedException exception) {
            LOGGER.error("Rejecting a webhook whose body could not be read as an event", exception);
            return ResponseEntity.badRequest().build();
        }

        InboxAdmission admission =
                webhookInboxService.accept(update, PayloadDigest.sha256Hex(rawPayload));

        // Both outcomes are a 200. A duplicate is not an error on Stripe's side:
        // at-least-once delivery is the contract, and absorbing the repeat is
        // precisely what the inbox is for.
        if (admission == InboxAdmission.ACCEPTED) {
            LOGGER.info("Webhook accepted: providerEventId={}", update.providerEventId().value());
        }
        return ResponseEntity.ok().build();
    }
}
