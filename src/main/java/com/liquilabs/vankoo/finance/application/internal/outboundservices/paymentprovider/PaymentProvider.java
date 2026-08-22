package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider;

import com.liquilabs.vankoo.finance.domain.exceptions.InvalidWebhookSignatureException;
import com.liquilabs.vankoo.finance.domain.exceptions.PaymentProviderException;
import com.liquilabs.vankoo.finance.domain.exceptions.RetryablePaymentProviderException;
import com.liquilabs.vankoo.finance.domain.exceptions.UnsupportedProviderEventException;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.IdempotencyKey;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositCreated;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositStatus;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.VerifiedProviderDepositUpdate;

/**
 * Outbound port towards payment providers.
 *
 * <p>This is the service's only deliberate dependency inversion: it is declared
 * here, in {@code application}, and implemented by
 * {@code infrastructure/providers/stripe}, so the use case can be tested with a
 * fake and without Stripe.
 *
 * <p>No provider SDK type crosses this boundary in either direction: not in the
 * parameters, not in the return values, not in the exceptions. Everything it
 * speaks in is a domain type — the port declares no vocabulary of its own.
 */
public interface PaymentProvider {

    /**
     * Creates the charge resource on the provider's side.
     *
     * @param idempotencyKey must be preserved across retries: a timeout is no
     *                       proof the charge was not created on the other side
     * @param description    optional text from the investor
     * @throws PaymentProviderException if the creation does not complete; see
     *         {@link RetryablePaymentProviderException} to tell whether retrying
     *         is worthwhile
     */
    ProviderDepositCreated createDeposit(IdempotencyKey idempotencyKey,
                                         DepositId depositId,
                                         Money amount,
                                         String description);

    /**
     * Reads the resource's current status from the provider.
     *
     * <p>This is the reconciliation path when a webhook is lost: Finance asks
     * instead of waiting.
     *
     * @throws PaymentProviderException if the read does not complete
     */
    ProviderDepositStatus getDeposit(Provider provider, ProviderDepositId providerDepositId);

    /**
     * Verifies the webhook's authenticity and normalizes its content.
     *
     * <p>The payload arrives raw and uninterpreted on purpose: the signature is
     * computed over the exact bytes the provider sent, so deserializing before
     * verifying would invalidate the check.
     *
     * @param rawPayload the HTTP body exactly as received, never re-serialized
     * @param signature  the provider's signature header
     * @throws InvalidWebhookSignatureException if the signature does not validate
     * @throws UnsupportedProviderEventException if the event is authentic but of
     *         a type this adapter does not normalize
     */
    VerifiedProviderDepositUpdate verifyWebhook(String rawPayload, String signature);
}
