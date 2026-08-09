package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider;

/**
 * Outbound port towards payment providers.
 *
 * <p>This is the service's only deliberate dependency inversion: it is declared
 * here, in {@code application}, and implemented by
 * {@code infrastructure/providers/stripe}, so the use case can be tested with a
 * fake and without Stripe.
 *
 * <p>No provider SDK type crosses this boundary in either direction: not in the
 * parameters, not in the return values, not in the exceptions.
 */

public interface PaymentProvider {


/**
 * Creates the charge resource on the provider's side.
 *
 * @throws PaymentProviderException if the creation does not complete; see
 *         {@link RetryablePaymentProviderException} to tell whether
 *         retrying is worthwhile
 *//*

    ProviderDepositCreated createDeposit(CreateProviderDepositRequest request);

    */
/**
 * Reads the resource's current status from the provider.
 *
 * <p>This is the reconciliation path when a webhook is lost: Finance asks
 * instead of waiting.
 *
 * @throws PaymentProviderException if the read does not complete
 *//*

    ProviderDepositStatus getDeposit(ProviderDepositReference reference);

    */
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
 *//*

    VerifiedProviderDepositUpdate verifyWebhook(String rawPayload, String signature);    */
}
