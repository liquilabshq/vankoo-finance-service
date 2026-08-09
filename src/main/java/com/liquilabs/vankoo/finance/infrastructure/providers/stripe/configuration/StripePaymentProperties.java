package com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "stripe")
public class StripePaymentProperties {
    private boolean enabled =  true;
    private String secretKey = "";
    private String webhookSecret = "";
    private String successUrl = "";
    private String cancelUrl = "";
    private String failureUrl = "";

    // =====================================================================
    // PROVISIONAL — delete this whole block in one piece.
    //
    // These are the PaymentProvider port's types. They belong in
    // application/internal/outboundservices/paymentprovider/{model,exceptions},
    // and they are nested here only because that layer is not in this branch
    // yet and this task creates no new files.
    //
    // A @ConfigurationProperties class is configuration, not a domain
    // taxonomy: nothing about this location is intentional beyond "it exists
    // already". When the domain layer lands, delete everything below and fix
    // the imports in StripePaymentProvider — no Stripe logic has to change,
    // because the shapes below match the contract exactly.
    //
    // Sources: docs/contracts/finance-contracts.md, «Contrato del puerto
    // PaymentProvider», and docs/uml/finance-application-services-class-diagram.puml.
    // =====================================================================

    /**
     * The Finance status taxonomy that adapters translate provider observations
     * into, before any command is issued.
     *
     * <p>It does not include {@code PENDING}: that is the initial status the
     * aggregate gives itself when it accepts the deposit, and it never comes
     * from an external observation.
     */
    public enum NormalizedDepositStatus {
        ACTION_REQUIRED,
        PROCESSING,
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    /**
     * Opaque reference the provider assigns to the deposit.
     *
     * <p>Its shape is never parsed nor validated: Stripe promises no particular
     * format, and validating one would break us the day they change it.
     */
    public record ProviderDepositId(String value) {

        public ProviderDepositId {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("providerDepositId must not be blank");
            }
        }
    }

    /**
     * Identifier of the provider's external event. It is the deduplication key
     * of the webhook inbox: {@code UNIQUE(provider, provider_event_id)}.
     *
     * <p>Opaque, just like {@link ProviderDepositId}.
     */
    public record ProviderEventId(String value) {

        public ProviderEventId {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("providerEventId must not be blank");
            }
        }
    }

    /** Idempotency key supplied by the client. Opaque: no format is imposed. */
    public record IdempotencyKey(String value) {

        public IdempotencyKey {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("idempotencyKey must not be blank");
            }
        }
    }

    /**
     * Input of {@code createDeposit}.
     *
     * <p>Money travels flattened as {@code amountMinor} + {@code currency}, the
     * same representation the contract already fixes for event JSON and for
     * Kafka. No {@code Money} value object is used because it does not exist in
     * {@code domain} yet, and {@code depositId} travels as a {@code String} for
     * the same reason.
     */
    public record CreateProviderDepositRequest(
            IdempotencyKey idempotencyKey,
            String depositId,
            long amountMinor,
            String currency,
            String description
    ) {

        public CreateProviderDepositRequest {
            if (idempotencyKey == null) {
                throw new IllegalArgumentException("idempotencyKey is required");
            }
            if (depositId == null || depositId.isBlank()) {
                throw new IllegalArgumentException("depositId is required");
            }
            if (amountMinor <= 0) {
                throw new IllegalArgumentException("amountMinor must be greater than zero");
            }
            // Only the ISO 4217 shape is checked here. Whether the currency belongs to
            // the supported catalogue is a business invariant, and the aggregate
            // enforces it before the port is ever invoked.
            if (currency == null || currency.length() != 3) {
                throw new IllegalArgumentException("currency must be a 3-letter ISO 4217 code");
            }
        }
    }

    /**
     * Output of {@code createDeposit}.
     *
     * <p>{@code actionUrl} is optional: it only arrives when the provider
     * requires the investor to complete an action.
     */
    public record ProviderDepositCreated(
            ProviderDepositId providerDepositId,
            String actionUrl,
            NormalizedDepositStatus status
    ) {

        public ProviderDepositCreated {
            if (providerDepositId == null) {
                throw new IllegalArgumentException("providerDepositId is required");
            }
            if (status == null) {
                throw new IllegalArgumentException("status is required");
            }
        }
    }

    /**
     * Input of {@code getDeposit}: identifies the deposit on the provider's
     * side.
     */
    public record ProviderDepositReference(String provider, ProviderDepositId providerDepositId) {

        public ProviderDepositReference {
            if (provider == null || provider.isBlank()) {
                throw new IllegalArgumentException("provider is required");
            }
            if (providerDepositId == null) {
                throw new IllegalArgumentException("providerDepositId is required");
            }
        }
    }

    /**
     * Output of {@code getDeposit}: the status observed at the provider,
     * already normalized to the Finance taxonomy.
     *
     * <p>{@code failureReason} only arrives when {@code status} is
     * {@link NormalizedDepositStatus#FAILED}, and it is a Finance-normalized
     * reason ({@code DECLINED}, {@code EXPIRED}, {@code INVALID_PAYMENT_METHOD},
     * {@code PROVIDER_ERROR}, {@code UNKNOWN}), never an internal Stripe code.
     */
    public record ProviderDepositStatus(
            ProviderDepositId providerDepositId,
            NormalizedDepositStatus status,
            Instant observedAt,
            String failureReason
    ) {

        public ProviderDepositStatus {
            if (providerDepositId == null || status == null || observedAt == null) {
                throw new IllegalArgumentException("providerDepositId, status and observedAt are required");
            }
        }
    }

    /**
     * Output of {@code verifyWebhook}: the external event, already
     * authenticated and normalized.
     *
     * <p>It deliberately carries <strong>no</strong> {@code depositId}.
     * Resolving {@code providerDepositId → depositId} is the webhook inbox's
     * job, against {@code finance_ops.deposit_provider_reference}, not the
     * port's: the provider does not know our identifiers.
     *
     * <p>It carries neither the raw payload nor the signature. Both stay in
     * {@code infrastructure}, per the contract rule against storing raw Stripe
     * payloads inside domain events.
     */
    public record VerifiedProviderDepositUpdate(
            String provider,
            ProviderDepositId providerDepositId,
            ProviderEventId providerEventId,
            NormalizedDepositStatus status,
            Instant observedAt,
            String failureReason,
            String cancellationReason
    ) {

        public VerifiedProviderDepositUpdate {
            if (provider == null || provider.isBlank()) {
                throw new IllegalArgumentException("provider is required");
            }
            if (providerDepositId == null || providerEventId == null || status == null || observedAt == null) {
                throw new IllegalArgumentException(
                        "providerDepositId, providerEventId, status and observedAt are required");
            }
        }
    }

    /**
     * Root of the payment port's errors. The adapter translates SDK errors into
     * these types: neither {@code application} nor {@code domain} ever sees a
     * Stripe exception.
     *
     * <p>It is {@code sealed} on purpose: a new error type has to be declared
     * here instead of leaking as a loose {@code RuntimeException} out of
     * {@code infrastructure/providers/stripe}.
     */
    public abstract static sealed class PaymentProviderException extends RuntimeException
            permits PaymentProviderTimeoutException,
            PaymentProviderUnavailableException,
            PaymentProviderRejectedException,
            InvalidWebhookSignatureException {

        protected PaymentProviderException(String message) {
            super(message);
        }

        protected PaymentProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Marks transient failures: retrying the same operation may yield a
     * different result.
     *
     * <p>It exists so whoever orchestrates the use case can decide whether to
     * retry without {@code instanceof} checks against concrete types and
     * without inspecting error messages by text.
     */
    public interface RetryablePaymentProviderException {
    }

    /**
     * The provider did not answer within the timeout configured by the adapter.
     *
     * <p>Retryable, but the retry must keep the same {@link IdempotencyKey}: a
     * timeout is no proof that the operation did not run on the other side.
     */
    public static final class PaymentProviderTimeoutException extends PaymentProviderException
            implements RetryablePaymentProviderException {

        public PaymentProviderTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The provider is down or returned a server error. Retryable. */
    public static final class PaymentProviderUnavailableException extends PaymentProviderException
            implements RetryablePaymentProviderException {

        public PaymentProviderUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * The provider definitively rejected the request: malformed request, bad
     * credentials, or a provider-side rule that is not met.
     *
     * <p>Not retryable. Note this is not the same as a declined charge: a charge
     * that fails is a business outcome and travels as
     * {@link NormalizedDepositStatus#FAILED}, not as an exception.
     */
    public static final class PaymentProviderRejectedException extends PaymentProviderException {

        public PaymentProviderRejectedException(String message) {
            super(message);
        }

        public PaymentProviderRejectedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * The webhook signature does not validate against the provider's secret.
     *
     * <p>Not retryable: the payload is not authentic and must reach neither the
     * inbox nor a command.
     */
    public static final class InvalidWebhookSignatureException extends PaymentProviderException {

        public InvalidWebhookSignatureException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ===================== end of PROVISIONAL block ======================
}