package com.liquilabs.vankoo.finance.application.internal.commandservices;

import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.PaymentProvider;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.CreateProviderDepositRequest;
import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.model.ProviderDepositCreated;

/**
 * Minimal use case proving Tarjeta 5's acceptance criterion: the payment flow
 * can be exercised with a fake provider, without Stripe.
 *
 * <p>Deliberately thin and <strong>without a Spring annotation</strong>: there
 * is no {@link PaymentProvider} implementation in {@code main} yet, so
 * registering this as a bean would break context startup.
 *
 * <p>Once the {@code Deposit} aggregate (Tarjeta 3) and the Stripe adapter
 * (Tarjeta 6) land, this becomes a real command handler: it will dispatch
 * {@code RegisterDepositProviderReferenceCommand} with the reference the
 * provider returned, rather than returning it.
 */
public class InitiateProviderDepositUseCase {

    private final PaymentProvider paymentProvider;

    public InitiateProviderDepositUseCase(PaymentProvider paymentProvider) {
        this.paymentProvider = paymentProvider;
    }

    public ProviderDepositCreated initiate(CreateProviderDepositRequest request) {
        return paymentProvider.createDeposit(request);
    }
}
