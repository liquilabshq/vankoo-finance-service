package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider;

import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.exceptions.PaymentProviderException;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.CreateProviderDepositRequest;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.NormalizedDepositStatus;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.ProviderDepositCreated;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.ProviderDepositId;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.ProviderDepositReference;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.ProviderDepositStatus;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.VerifiedProviderDepositUpdate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory implementation of {@link PaymentProvider}.
 *
 * <p>This is the proof of Tarjeta 5's acceptance criterion: if the use case can
 * be exercised against this without a single Stripe class, the port is properly
 * isolated.
 *
 * <p>The {@code failNextXWith} methods program a single-use failure: it is
 * consumed when it fires, so one test cannot contaminate the next.
 */
public class FakePaymentProvider implements PaymentProvider {

    private final Map<ProviderDepositId, ProviderDepositStatus> depositsByProviderId = new HashMap<>();

    private PaymentProviderException nextCreateFailure;
    private PaymentProviderException nextGetFailure;
    private PaymentProviderException nextWebhookFailure;
    private VerifiedProviderDepositUpdate nextWebhookResult;

    private int sequence = 0;

    @Override
    public ProviderDepositCreated createDeposit(CreateProviderDepositRequest request) {
        throwIfProgrammed(nextCreateFailure);

        ProviderDepositId providerDepositId = new ProviderDepositId("fake-provider-id-" + (++sequence));
        depositsByProviderId.put(
                providerDepositId,
                new ProviderDepositStatus(providerDepositId, NormalizedDepositStatus.PROCESSING, Instant.now(), null));

        return new ProviderDepositCreated(providerDepositId, null, NormalizedDepositStatus.PROCESSING);
    }

    @Override
    public ProviderDepositStatus getDeposit(ProviderDepositReference reference) {
        throwIfProgrammed(nextGetFailure);

        ProviderDepositStatus status = depositsByProviderId.get(reference.providerDepositId());
        if (status == null) {
            throw new IllegalStateException(
                    "The fake does not know deposit " + reference.providerDepositId().value()
                            + ". Create it with createDeposit or program it with advanceDepositTo.");
        }
        return status;
    }

    @Override
    public VerifiedProviderDepositUpdate verifyWebhook(String rawPayload, String signature) {
        throwIfProgrammed(nextWebhookFailure);

        if (nextWebhookResult == null) {
            throw new IllegalStateException(
                    "No webhook result programmed. Use programNextWebhookResult or failNextWebhookWith.");
        }
        return nextWebhookResult;
    }

    /** Moves the deposit to the given status, as the provider would over time. */
    public void advanceDepositTo(ProviderDepositId providerDepositId,
                                 NormalizedDepositStatus status,
                                 String failureReason) {
        depositsByProviderId.put(
                providerDepositId,
                new ProviderDepositStatus(providerDepositId, status, Instant.now(), failureReason));
    }

    public void failNextCreateWith(PaymentProviderException exception) {
        this.nextCreateFailure = exception;
    }

    public void failNextGetWith(PaymentProviderException exception) {
        this.nextGetFailure = exception;
    }

    public void failNextWebhookWith(PaymentProviderException exception) {
        this.nextWebhookFailure = exception;
    }

    public void programNextWebhookResult(VerifiedProviderDepositUpdate result) {
        this.nextWebhookResult = result;
    }

    private void throwIfProgrammed(PaymentProviderException failure) {
        if (failure == null) {
            return;
        }
        clearProgrammedFailure(failure);
        throw failure;
    }

    private void clearProgrammedFailure(PaymentProviderException failure) {
        if (failure == nextCreateFailure) {
            nextCreateFailure = null;
        }
        if (failure == nextGetFailure) {
            nextGetFailure = null;
        }
        if (failure == nextWebhookFailure) {
            nextWebhookFailure = null;
        }
    }
}
