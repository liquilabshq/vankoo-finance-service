package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider;

import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.exceptions.InvalidWebhookSignatureException;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.exceptions.PaymentProviderUnavailableException;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.exceptions.RetryablePaymentProviderException;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.CreateProviderDepositRequest;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.IdempotencyKey;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.NormalizedDepositStatus;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.ProviderDepositCreated;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.ProviderDepositId;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.ProviderDepositReference;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.ProviderDepositStatus;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.ProviderEventId;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.VerifiedProviderDepositUpdate;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the port directly, on the paths that do not go through the creation
 * use case: status reads and webhook verification.
 */
class PaymentProviderPortTest {

    private final FakePaymentProvider fakePaymentProvider = new FakePaymentProvider();

    @Test
    void readsBackTheStatusOfACreatedDeposit() {
        ProviderDepositCreated created = fakePaymentProvider.createDeposit(new CreateProviderDepositRequest(
                new IdempotencyKey("idem-key-1"), "deposit-1", 12500, "PEN", null));

        ProviderDepositStatus status = fakePaymentProvider.getDeposit(
                new ProviderDepositReference("STRIPE", created.providerDepositId()));

        assertThat(status.status()).isEqualTo(NormalizedDepositStatus.PROCESSING);
        assertThat(status.failureReason()).isNull();
    }

    @Test
    void reflectsAStatusAdvancedByTheProvider() {
        ProviderDepositCreated created = fakePaymentProvider.createDeposit(new CreateProviderDepositRequest(
                new IdempotencyKey("idem-key-2"), "deposit-2", 12500, "PEN", null));
        fakePaymentProvider.advanceDepositTo(
                created.providerDepositId(), NormalizedDepositStatus.FAILED, "DECLINED");

        ProviderDepositStatus status = fakePaymentProvider.getDeposit(
                new ProviderDepositReference("STRIPE", created.providerDepositId()));

        assertThat(status.status()).isEqualTo(NormalizedDepositStatus.FAILED);
        assertThat(status.failureReason()).isEqualTo("DECLINED");
    }

    @Test
    void verifiesAWebhookAndNormalizesIt() {
        ProviderDepositId providerDepositId = new ProviderDepositId("provider-ref-1");
        VerifiedProviderDepositUpdate expected = new VerifiedProviderDepositUpdate(
                "STRIPE",
                providerDepositId,
                new ProviderEventId("evt_1"),
                NormalizedDepositStatus.SUCCEEDED,
                Instant.parse("2026-07-26T15:30:00Z"),
                null,
                null);
        fakePaymentProvider.programNextWebhookResult(expected);

        VerifiedProviderDepositUpdate result = fakePaymentProvider.verifyWebhook("{\"id\":\"evt_1\"}", "valid-sig");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void rejectsAWebhookWithAnInvalidSignature() {
        fakePaymentProvider.failNextWebhookWith(new InvalidWebhookSignatureException("invalid signature"));

        assertThatThrownBy(() -> fakePaymentProvider.verifyWebhook("{\"id\":\"evt_1\"}", "bad-sig"))
                .isInstanceOf(InvalidWebhookSignatureException.class)
                .isNotInstanceOf(RetryablePaymentProviderException.class);
    }

    @Test
    void surfacesProviderOutageAsRetryableOnStatusReads() {
        fakePaymentProvider.failNextGetWith(
                new PaymentProviderUnavailableException("provider returned 503", new RuntimeException("upstream")));

        assertThatThrownBy(() -> fakePaymentProvider.getDeposit(
                new ProviderDepositReference("STRIPE", new ProviderDepositId("provider-ref-2"))))
                .isInstanceOf(PaymentProviderUnavailableException.class)
                .isInstanceOf(RetryablePaymentProviderException.class);
    }
}
