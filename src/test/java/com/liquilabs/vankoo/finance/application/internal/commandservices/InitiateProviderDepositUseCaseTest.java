package com.liquilabs.vankoo.finance.application.internal.commandservices;

import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.FakePaymentProvider;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.exceptions.PaymentProviderRejectedException;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.exceptions.PaymentProviderTimeoutException;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.exceptions.RetryablePaymentProviderException;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.CreateProviderDepositRequest;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.IdempotencyKey;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.NormalizedDepositStatus;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.ProviderDepositCreated;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarjeta 5's acceptance criterion: the payment use case can be tested with a
 * fake provider, without depending on Stripe.
 */
class InitiateProviderDepositUseCaseTest {

    private final FakePaymentProvider fakePaymentProvider = new FakePaymentProvider();
    private final InitiateProviderDepositUseCase useCase = new InitiateProviderDepositUseCase(fakePaymentProvider);

    @Test
    void createsADepositThroughThePort() {
        CreateProviderDepositRequest request = new CreateProviderDepositRequest(
                new IdempotencyKey("idem-key-1"),
                "2b7f1f7d-9f67-4a46-9db8-f44ca7e43d0a",
                12500,
                "PEN",
                "Recarga de saldo");

        ProviderDepositCreated result = useCase.initiate(request);

        assertThat(result.providerDepositId().value()).isNotBlank();
        assertThat(result.status()).isEqualTo(NormalizedDepositStatus.PROCESSING);
    }

    @Test
    void propagatesATimeoutAsARetryableFailure() {
        fakePaymentProvider.failNextCreateWith(
                new PaymentProviderTimeoutException("simulated timeout", new RuntimeException("socket timeout")));

        assertThatThrownBy(() -> useCase.initiate(requestWith("idem-key-2")))
                .isInstanceOf(PaymentProviderTimeoutException.class)
                .isInstanceOf(RetryablePaymentProviderException.class);
    }

    @Test
    void propagatesARejectionAsANonRetryableFailure() {
        fakePaymentProvider.failNextCreateWith(new PaymentProviderRejectedException("invalid request"));

        assertThatThrownBy(() -> useCase.initiate(requestWith("idem-key-3")))
                .isInstanceOf(PaymentProviderRejectedException.class)
                .isNotInstanceOf(RetryablePaymentProviderException.class);
    }

    @Test
    void consumesTheProgrammedFailureOnlyOnce() {
        fakePaymentProvider.failNextCreateWith(
                new PaymentProviderTimeoutException("simulated timeout", new RuntimeException("socket timeout")));

        assertThatThrownBy(() -> useCase.initiate(requestWith("idem-key-4")))
                .isInstanceOf(PaymentProviderTimeoutException.class);

        // The retry keeps the same idempotency key and now succeeds.
        ProviderDepositCreated retried = useCase.initiate(requestWith("idem-key-4"));

        assertThat(retried.status()).isEqualTo(NormalizedDepositStatus.PROCESSING);
    }

    @Test
    void rejectsARequestWithoutAnIdempotencyKey() {
        assertThatThrownBy(() -> new CreateProviderDepositRequest(
                null, "2b7f1f7d-9f67-4a46-9db8-f44ca7e43d0a", 12500, "PEN", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotencyKey");
    }

    @Test
    void rejectsANonPositiveAmount() {
        assertThatThrownBy(() -> new CreateProviderDepositRequest(
                new IdempotencyKey("idem-key-5"), "2b7f1f7d-9f67-4a46-9db8-f44ca7e43d0a", 0, "PEN", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amountMinor");
    }

    private CreateProviderDepositRequest requestWith(String idempotencyKey) {
        return new CreateProviderDepositRequest(
                new IdempotencyKey(idempotencyKey),
                "2b7f1f7d-9f67-4a46-9db8-f44ca7e43d0a",
                5000,
                "USD",
                null);
    }
}
