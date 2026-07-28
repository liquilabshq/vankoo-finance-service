package com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.exceptions;

/**
 * Marks transient failures: retrying the same operation may yield a different
 * result.
 *
 * <p>It exists so whoever orchestrates the use case can decide whether to retry
 * without {@code instanceof} checks against concrete types and without
 * inspecting error messages by text.
 */
public interface RetryablePaymentProviderException {
}
