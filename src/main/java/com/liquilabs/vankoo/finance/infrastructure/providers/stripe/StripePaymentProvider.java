package com.liquilabs.vankoo.finance.infrastructure.providers.stripe;

import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.PaymentProvider;
import com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration.StripePaymentProperties;
import com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration.StripePaymentProperties.CreateProviderDepositRequest;
import com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration.StripePaymentProperties.InvalidWebhookSignatureException;
import com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration.StripePaymentProperties.NormalizedDepositStatus;
import com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration.StripePaymentProperties.PaymentProviderException;
import com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration.StripePaymentProperties.PaymentProviderRejectedException;
import com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration.StripePaymentProperties.PaymentProviderTimeoutException;
import com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration.StripePaymentProperties.PaymentProviderUnavailableException;
import com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration.StripePaymentProperties.ProviderDepositCreated;
import com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration.StripePaymentProperties.ProviderDepositId;
import com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration.StripePaymentProperties.ProviderDepositReference;
import com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration.StripePaymentProperties.ProviderDepositStatus;
import com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration.StripePaymentProperties.ProviderEventId;
import com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration.StripePaymentProperties.VerifiedProviderDepositUpdate;
import com.stripe.Stripe;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.IdempotencyException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Stripe implementation of the payment port, backed by Checkout Sessions.
 *
 * <p>The Checkout Session is what a Vankoo deposit maps to: its id
 * ({@code cs_...}) becomes the {@code ProviderDepositId}, and its hosted URL
 * becomes the {@code actionUrl} the investor must visit. Only
 * {@code checkout.session.*} events are understood — a {@code payment_intent.*}
 * event carries a {@code pi_...} id, which is not what
 * {@code finance_ops.deposit_provider_reference} has registered and would
 * resolve to no deposit at all.
 *
 * <p>No Stripe type leaves this class: SDK models are mapped to the port's
 * records and {@link StripeException} is translated into the sealed
 * {@link PaymentProviderException} hierarchy.
 *
 * <p>Neither timeout nor retry live here. The contract assigns them to whoever
 * orchestrates the use case; this adapter only <em>signals</em> which failures
 * are transient, through {@code RetryablePaymentProviderException}.
 *
 * <p>TODO: the port's types are temporarily nested in
 * {@link StripePaymentProperties}. When the domain layer lands, only the
 * imports above change — nothing in the logic below does.
 */
@Service
public class StripePaymentProvider implements PaymentProvider {

    /**
     * Value of the {@code provider} field. A private constant until the domain
     * has an enum of supported providers.
     */
    private static final String PROVIDER = "stripe";

    private static final String PRODUCT_NAME = "Vankoo balance top-up";

    /**
     * The only event types this adapter understands. It is also the exact list
     * the Stripe webhook endpoint must be subscribed to: anything else reaching
     * us is a misconfiguration, not a deposit update.
     *
     * <p>TODO (Tarjeta 7): the port's signature has to return a non-null update,
     * so an unexpected type can only be rejected here. When the webhook
     * controller exists, it should decide the status code for that case.
     */
    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "checkout.session.completed",
            "checkout.session.async_payment_succeeded",
            "checkout.session.async_payment_failed",
            "checkout.session.expired");

    private final StripePaymentProperties stripePaymentProperties;
    private static final Logger LOGGER = LoggerFactory.getLogger(StripePaymentProvider.class);

    public StripePaymentProvider(StripePaymentProperties stripePaymentProperties) {
        this.stripePaymentProperties = stripePaymentProperties;
    }

    /**
     * Creates the Checkout Session that will collect the investor's top-up.
     *
     * @throws PaymentProviderException if the creation does not complete
     */
    public ProviderDepositCreated createDeposit(CreateProviderDepositRequest request) {
        requireUsableCredentials();
        requireConfigured(stripePaymentProperties.getSuccessUrl(), "stripe.success-url");
        requireConfigured(stripePaymentProperties.getCancelUrl(), "stripe.cancel-url");

        Stripe.apiKey = stripePaymentProperties.getSecretKey();

        var params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(stripePaymentProperties.getSuccessUrl())
                .setCancelUrl(stripePaymentProperties.getCancelUrl())
                // Two independent ways back to the deposit: client_reference_id is what
                // Stripe echoes in the dashboard, metadata is what survives in the API.
                .setClientReferenceId(request.depositId())
                .putMetadata("deposit_id", request.depositId())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(request.currency().toLowerCase())
                                                // amountMinor is ALREADY in minor units, unlike the
                                                // BigDecimal amounts other services carry. Do not
                                                // scale it here or every deposit is charged x100.
                                                .setUnitAmount(request.amountMinor())
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName(PRODUCT_NAME)
                                                                .setDescription(productDescription(request))
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();

        // This is the contract's mapping of IdempotencyKey onto Stripe's own
        // Idempotency-Key header: it stops a retry from creating a second charge.
        var options = RequestOptions.builder()
                .setIdempotencyKey(request.idempotencyKey().value())
                .build();

        try {
            Session session = Session.create(params, options);
            // A session is born open/unpaid with its hosted URL present, so the
            // investor always has an action pending at this point.
            return new ProviderDepositCreated(
                    new ProviderDepositId(session.getId()),
                    session.getUrl(),
                    NormalizedDepositStatus.ACTION_REQUIRED);
        } catch (StripeException exception) {
            throw mapStripeException("create checkout session", exception);
        }
    }

    /**
     * Reads the session's current status from Stripe.
     *
     * <p>This is the reconciliation path when a webhook is lost: Finance asks
     * instead of waiting.
     *
     * @throws PaymentProviderException if the read does not complete
     */
    public ProviderDepositStatus getDeposit(ProviderDepositReference reference) {
        requireUsableCredentials();

        Stripe.apiKey = stripePaymentProperties.getSecretKey();

        try {
            Session session = Session.retrieve(reference.providerDepositId().value());
            return new ProviderDepositStatus(
                    new ProviderDepositId(session.getId()),
                    normalize(session),
                    // We observed it now; Stripe reports no observation timestamp.
                    Instant.now(),
                    null);
        } catch (StripeException exception) {
            throw mapStripeException("retrieve checkout session", exception);
        }
    }

    /**
     * Verifies the webhook's authenticity and normalizes its content.
     *
     * <p>The payload arrives raw and uninterpreted on purpose: the signature is
     * computed over the exact bytes Stripe sent, so deserializing before
     * verifying would invalidate the check.
     *
     * @param rawPayload the HTTP body exactly as received, never re-serialized
     * @param signature  the {@code Stripe-Signature} header
     * @throws InvalidWebhookSignatureException if the signature does not validate
     */
    public VerifiedProviderDepositUpdate verifyWebhook(String rawPayload, String signature) {
        requireConfigured(stripePaymentProperties.getWebhookSecret(), "stripe.webhook-secret");

        Event event;
        try {
            event = Webhook.constructEvent(rawPayload, signature, stripePaymentProperties.getWebhookSecret());
        } catch (SignatureVerificationException exception) {
            // Deliberately logged without the payload: an unverified body is not
            // evidence of anything and must not be persisted anywhere.
            LOGGER.warn("Stripe webhook signature verification failed: {}", exception.getMessage());
            throw new InvalidWebhookSignatureException(
                    "Stripe webhook signature does not validate", exception);
        } catch (RuntimeException exception) {
            // constructEvent parses before it verifies, so a malformed body throws
            // Gson's JsonSyntaxException, and a v2 event notification throws
            // IllegalArgumentException. Both are caught as RuntimeException because
            // Gson is a runtime-scope dependency of stripe-java and is not on our
            // compile classpath. Either way the failure is definitive, never retried.
            throw new PaymentProviderRejectedException(
                    "Stripe webhook payload could not be parsed as an event", exception);
        }

        String eventType = event.getType();
        // Checked before touching the payload so that an unrelated event reports the
        // type it actually had, instead of failing later as "not a Checkout Session".
        if (!SUPPORTED_EVENT_TYPES.contains(eventType)) {
            throw new PaymentProviderRejectedException("Unsupported Stripe event type: " + eventType);
        }

        Session session = extractSession(event);

        NormalizedDepositStatus status = switch (eventType) {
            case "checkout.session.completed" -> normalizePaymentStatus(session);
            case "checkout.session.async_payment_succeeded" -> NormalizedDepositStatus.SUCCEEDED;
            case "checkout.session.async_payment_failed" -> NormalizedDepositStatus.FAILED;
            case "checkout.session.expired" -> NormalizedDepositStatus.CANCELLED;
            default -> throw new IllegalStateException("Unreachable event type: " + eventType);
        };

        return new VerifiedProviderDepositUpdate(
                PROVIDER,
                new ProviderDepositId(session.getId()),
                new ProviderEventId(event.getId()),
                status,
                Instant.ofEpochSecond(event.getCreated()),
                // TODO: Checkout Session exposes no normalized failure reason, so a failed
                // async payment can only be reported as UNKNOWN. Resolved when the contract's
                // FailureReason enum lands in domain/model/valueobjects.
                status == NormalizedDepositStatus.FAILED ? "UNKNOWN" : null,
                status == NormalizedDepositStatus.CANCELLED ? "EXPIRED" : null);
    }

    /**
     * Maps the session's own lifecycle onto the Finance taxonomy.
     *
     * <p>{@code status} is {@code open}, {@code complete} or {@code expired};
     * {@code payment_status} is {@code paid}, {@code unpaid} or
     * {@code no_payment_required}.
     */
    private NormalizedDepositStatus normalize(Session session) {
        return switch (session.getStatus()) {
            case "open" -> NormalizedDepositStatus.ACTION_REQUIRED;
            case "complete" -> normalizePaymentStatus(session);
            case "expired" -> NormalizedDepositStatus.CANCELLED;
            default -> throw new PaymentProviderRejectedException(
                    "Unsupported Stripe session status: " + session.getStatus());
        };
    }

    /**
     * A completed session is not necessarily a collected one: delayed methods
     * finish the session while the money is still in flight, and those stay
     * {@code unpaid} until an async_payment_* event resolves them.
     */
    private NormalizedDepositStatus normalizePaymentStatus(Session session) {
        String paymentStatus = session.getPaymentStatus();
        boolean collected = "paid".equals(paymentStatus) || "no_payment_required".equals(paymentStatus);
        return collected ? NormalizedDepositStatus.SUCCEEDED : NormalizedDepositStatus.PROCESSING;
    }

    /**
     * Pulls the Checkout Session out of the event envelope.
     *
     * <p>The deserializer returns empty when the account's API version differs
     * from the SDK's. Falling back to {@code deserializeUnsafe} keeps that from
     * silently dropping an otherwise valid webhook.
     */
    private Session extractSession(Event event) {
        var deserializer = event.getDataObjectDeserializer();
        Optional<StripeObject> object = deserializer.getObject();

        StripeObject stripeObject;
        if (object.isPresent()) {
            stripeObject = object.get();
        } else {
            try {
                stripeObject = deserializer.deserializeUnsafe();
            } catch (Exception exception) {
                throw new PaymentProviderRejectedException(
                        "Could not deserialize the object of Stripe event " + event.getId(), exception);
            }
        }

        if (!(stripeObject instanceof Session session)) {
            throw new PaymentProviderRejectedException(
                    "Stripe event " + event.getId() + " does not carry a Checkout Session");
        }
        return session;
    }

    /**
     * Translates an SDK error into the port's sealed hierarchy, so that neither
     * {@code application} nor {@code domain} ever sees a Stripe exception.
     *
     * <p>A declined charge does not come through here: that is a business
     * outcome and travels as {@code FAILED} inside a status, because a decline
     * must produce {@code DepositFailedEvent} while a port error produces no
     * domain event at all.
     */
    private PaymentProviderException mapStripeException(String operation, StripeException exception) {
        // statusCode, code and requestId are what makes a failure findable in the
        // Stripe dashboard. The payload and the secret are never logged.
        LOGGER.error("Stripe call failed: operation={}, statusCode={}, code={}, requestId={}, message={}",
                operation,
                exception.getStatusCode(),
                exception.getCode(),
                exception.getRequestId(),
                exception.getMessage(),
                exception);

        String message = "Failed to %s: %s".formatted(operation, exception.getMessage());

        return switch (exception) {
            case ApiConnectionException e -> new PaymentProviderTimeoutException(message, e);
            case RateLimitException e -> new PaymentProviderUnavailableException(message, e);
            case ApiException e -> new PaymentProviderUnavailableException(message, e);
            case AuthenticationException e -> new PaymentProviderRejectedException(message, e);
            case InvalidRequestException e -> new PaymentProviderRejectedException(message, e);
            case CardException e -> new PaymentProviderRejectedException(message, e);
            case IdempotencyException e -> new PaymentProviderRejectedException(message, e);
            default -> new PaymentProviderRejectedException(message, exception);
        };
    }

    /**
     * A missing or disabled configuration is our own deployment error, not an
     * answer from Stripe, so it stays outside the sealed hierarchy: retrying it
     * or mapping it to a deposit outcome would both be wrong.
     */
    private void requireUsableCredentials() {
        if (!stripePaymentProperties.isEnabled()) {
            throw new IllegalStateException("Stripe payments are disabled (stripe.enabled=false)");
        }
        requireConfigured(stripePaymentProperties.getSecretKey(), "stripe.secret-key");
    }

    private void requireConfigured(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("%s is not configured".formatted(propertyName));
        }
    }

    private String productDescription(CreateProviderDepositRequest request) {
        if (request.description() == null || request.description().isBlank()) {
            return "Deposit %s".formatted(request.depositId());
        }
        return request.description();
    }
}