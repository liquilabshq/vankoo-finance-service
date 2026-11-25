package com.liquilabs.vankoo.finance.infrastructure.providers.stripe;

import com.liquilabs.vankoo.finance.domain.exceptions.PaymentProviderRejectedException;
import com.liquilabs.vankoo.finance.domain.exceptions.PaymentProviderTimeoutException;
import com.liquilabs.vankoo.finance.domain.exceptions.PaymentProviderUnavailableException;
import com.liquilabs.vankoo.finance.domain.exceptions.RetryablePaymentProviderException;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.IdempotencyKey;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.NormalizedDepositStatus;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositCreated;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositStatus;
import com.liquilabs.vankoo.finance.infrastructure.providers.stripe.configuration.StripePaymentProperties;
import com.stripe.Stripe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba de integración del adaptador de Stripe contra un stub HTTP de su API.
 *
 * <p>Se usa el SDK real ({@code stripe-java}) apuntado con
 * {@link Stripe#overrideApiBase(String)} a {@link StripeApiStub}. Así se verifica
 * el contrato de red completo (ruta, encabezados, cuerpo form-urlencoded y
 * deserialización de la respuesta) sin cuenta de Stripe ni acceso a internet.
 *
 * @author LiquiLabs
 */
class StripePaymentProviderStubServerTest {

    private static final DepositId DEPOSIT_ID = DepositId.of("0199a3e4-7b1c-7d2e-8f3a-1b2c3d4e5f60");
    private static final IdempotencyKey IDEMPOTENCY_KEY = new IdempotencyKey("idem-key-123");
    private static final Money AMOUNT = new Money(15_000, Currency.PEN);

    private StripeApiStub stripe;
    private StripePaymentProperties properties;
    private StripePaymentProvider provider;
    private int previousRetries;

    @BeforeEach
    void setUp() throws IOException {
        stripe = new StripeApiStub();
        Stripe.overrideApiBase(stripe.baseUrl());
        // Sin reintentos del SDK: cada solicitud del adaptador es una sola llamada observable.
        previousRetries = Stripe.getMaxNetworkRetries();
        Stripe.setMaxNetworkRetries(0);

        properties = new StripePaymentProperties();
        properties.setSecretKey("sk_test_stub");
        properties.setWebhookSecret("whsec_stub");
        properties.setSuccessUrl("https://vankoo.test/top-up/success");
        properties.setCancelUrl("https://vankoo.test/top-up/cancel");
        provider = new StripePaymentProvider(properties);
    }

    @AfterEach
    void tearDown() {
        stripe.close();
        Stripe.overrideApiBase(Stripe.LIVE_API_BASE);
        Stripe.setMaxNetworkRetries(previousRetries);
        Stripe.apiKey = null;
    }

    @Test
    void createDeposit_sendsTheCheckoutSessionAndMapsTheHostedUrl() {
        stripe.willRespond(200, StripeApiStub.session("cs_test_a1", "open", "unpaid"));

        ProviderDepositCreated created =
                provider.createDeposit(IDEMPOTENCY_KEY, DEPOSIT_ID, AMOUNT, "Recarga de saldo");

        assertThat(created.providerDepositId()).isEqualTo(new ProviderDepositId("cs_test_a1"));
        assertThat(created.actionUrl()).isEqualTo("https://checkout.stripe.com/c/pay/cs_test_a1");
        assertThat(created.status()).isEqualTo(NormalizedDepositStatus.ACTION_REQUIRED);

        var request = stripe.requests().getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1/checkout/sessions");
        assertThat(request.header("Authorization")).isEqualTo("Bearer sk_test_stub");
        assertThat(request.decodedBody())
                .contains("mode=payment")
                .contains("client_reference_id=" + DEPOSIT_ID)
                .contains("metadata[deposit_id]=" + DEPOSIT_ID)
                .contains("line_items[0][price_data][currency]=pen")
                .contains("success_url=https://vankoo.test/top-up/success")
                .contains("cancel_url=https://vankoo.test/top-up/cancel");
    }

    @Test
    void createDeposit_sendsTheAmountAlreadyInMinorUnits() {
        stripe.willRespond(200, StripeApiStub.session("cs_test_a2", "open", "unpaid"));

        provider.createDeposit(IDEMPOTENCY_KEY, DEPOSIT_ID, AMOUNT, null);

        // 15000 céntimos = S/ 150.00. Escalarlo otra vez cobraría x100.
        assertThat(stripe.requests().getFirst().decodedBody())
                .contains("line_items[0][price_data][unit_amount]=15000")
                .doesNotContain("unit_amount=1500000");
    }

    @Test
    void createDeposit_forwardsTheIdempotencyKeySoARetryCannotChargeTwice() {
        stripe.willRespond(200, StripeApiStub.session("cs_test_a3", "open", "unpaid"));

        provider.createDeposit(IDEMPOTENCY_KEY, DEPOSIT_ID, AMOUNT, null);

        assertThat(stripe.requests().getFirst().header("Idempotency-Key")).isEqualTo("idem-key-123");
    }

    @Test
    void createDeposit_rejectsANonPositiveAmountWithoutCallingStripe() {
        assertThatThrownBy(() -> provider.createDeposit(
                IDEMPOTENCY_KEY, DEPOSIT_ID, new Money(0, Currency.PEN), null))
                .isInstanceOf(PaymentProviderRejectedException.class);

        assertThat(stripe.requests()).isEmpty();
    }

    @Test
    void createDeposit_failsFastWhenStripeIsDisabled() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> provider.createDeposit(IDEMPOTENCY_KEY, DEPOSIT_ID, AMOUNT, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stripe.enabled=false");
        assertThat(stripe.requests()).isEmpty();
    }

    /**
     * Cada error HTTP de Stripe se traduce a la jerarquía sellada del puerto. Solo
     * los transitorios (429 y 5xx) quedan marcados como reintentables.
     */
    @ParameterizedTest(name = "HTTP {0} ({1}) -> {2}")
    @CsvSource({
            "400, invalid_request_error, REJECTED",
            "401, invalid_request_error, REJECTED",
            "402, card_error,            REJECTED",
            "429, invalid_request_error, UNAVAILABLE",
            "500, api_error,             UNAVAILABLE",
            "503, api_error,             UNAVAILABLE"
    })
    void createDeposit_translatesStripeErrorsIntoThePortHierarchy(int status, String type, String expected) {
        stripe.willRespond(status, StripeApiStub.error(type, "stubbed failure"));

        Class<? extends RuntimeException> expectedType = switch (expected) {
            case "REJECTED" -> PaymentProviderRejectedException.class;
            case "UNAVAILABLE" -> PaymentProviderUnavailableException.class;
            default -> throw new IllegalArgumentException(expected);
        };

        assertThatThrownBy(() -> provider.createDeposit(IDEMPOTENCY_KEY, DEPOSIT_ID, AMOUNT, null))
                .isInstanceOf(expectedType)
                .satisfies(error -> assertThat(error instanceof RetryablePaymentProviderException)
                        .isEqualTo("UNAVAILABLE".equals(expected)));
    }

    @Test
    void createDeposit_reportsAnUnreachableStripeAsARetryableTimeout() throws IOException {
        // Un puerto que estuvo abierto y ya no lo está: conexión rechazada.
        String closedBaseUrl;
        try (var closed = new StripeApiStub()) {
            closedBaseUrl = closed.baseUrl();
        }
        Stripe.overrideApiBase(closedBaseUrl);

        assertThatThrownBy(() -> provider.createDeposit(IDEMPOTENCY_KEY, DEPOSIT_ID, AMOUNT, null))
                .isInstanceOf(PaymentProviderTimeoutException.class)
                .isInstanceOf(RetryablePaymentProviderException.class);
    }

    @Test
    void errorsNeverCarryTheSecretKey() {
        stripe.willRespond(401, StripeApiStub.error("invalid_request_error", "Invalid API Key provided"));

        assertThatThrownBy(() -> provider.createDeposit(IDEMPOTENCY_KEY, DEPOSIT_ID, AMOUNT, null))
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("sk_test_stub"));
    }

    /** Reconciliación: el estado de la sesión se normaliza a la taxonomía de Finance. */
    @ParameterizedTest(name = "status={0}, payment_status={1} -> {2}")
    @CsvSource({
            "open,     unpaid,              ACTION_REQUIRED",
            "complete, paid,                SUCCEEDED",
            "complete, no_payment_required, SUCCEEDED",
            "complete, unpaid,              PROCESSING",
            "expired,  unpaid,              CANCELLED"
    })
    void getDeposit_normalizesTheSessionStatus(String status, String paymentStatus, NormalizedDepositStatus expected) {
        stripe.willRespond(200, StripeApiStub.session("cs_test_r1", status, paymentStatus));

        ProviderDepositStatus result = provider.getDeposit(Provider.STRIPE, new ProviderDepositId("cs_test_r1"));

        assertThat(result.status()).isEqualTo(expected);
        assertThat(result.providerDepositId().value()).isEqualTo("cs_test_r1");
        var request = stripe.requests().getFirst();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).isEqualTo("/v1/checkout/sessions/cs_test_r1");
    }

    @Test
    void getDeposit_rejectsAnUnknownSessionStatus() {
        stripe.willRespond(200, StripeApiStub.session("cs_test_r2", "something_new", "unpaid"));

        assertThatThrownBy(() -> provider.getDeposit(Provider.STRIPE, new ProviderDepositId("cs_test_r2")))
                .isInstanceOf(PaymentProviderRejectedException.class)
                .hasMessageContaining("something_new");
    }

    @Test
    void getDeposit_mapsAMissingSessionToRejected() {
        stripe.willRespond(404, StripeApiStub.error("invalid_request_error", "No such checkout.session"));

        assertThatThrownBy(() -> provider.getDeposit(Provider.STRIPE, new ProviderDepositId("cs_test_missing")))
                .isInstanceOf(PaymentProviderRejectedException.class);
    }
}
