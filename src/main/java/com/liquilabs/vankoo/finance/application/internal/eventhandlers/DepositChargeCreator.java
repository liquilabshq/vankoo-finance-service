package com.liquilabs.vankoo.finance.application.internal.eventhandlers;

import com.liquilabs.vankoo.finance.application.internal.outboundservices.paymentprovider.PaymentProvider;
import com.liquilabs.vankoo.finance.domain.exceptions.PaymentProviderException;
import com.liquilabs.vankoo.finance.domain.exceptions.RetryablePaymentProviderException;
import com.liquilabs.vankoo.finance.domain.model.commands.RegisterDepositProviderReferenceCommand;
import com.liquilabs.vankoo.finance.domain.model.events.DepositInitiatedEvent;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.IdempotencyKey;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositCreated;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Creates the charge on the provider's side once a deposit has been accepted,
 * and registers the reference it answers with.
 *
 * <p>Without this handler a deposit stays {@code PENDING} forever: the
 * {@code InitiateDepositCommand} handler does no I/O — it validates and applies
 * {@code DepositInitiatedEvent} — so nothing would ever reach Stripe.
 *
 * <p>A plain {@code @EventHandler}, not a saga, on the mould of
 * {@code WalletCreditor}: one outbound call and one command dispatch, with
 * nothing to wait for and nothing to compensate.
 *
 * <p><strong>It does not move the deposit's status.</strong> {@code createDeposit}
 * answers {@code ACTION_REQUIRED}, but {@code ApplyProviderDepositUpdateCommand}
 * requires a {@code providerEventId} that this response does not carry — its
 * compact constructor rejects a missing one, because without it the inbox has
 * nothing to deduplicate on. So only the webhook moves the status, and the
 * {@code actionUrl} travels on the registration command instead, which is where
 * the contract already puts it.
 *
 * <p><strong>Retries are Axon's.</strong> A transient failure is rethrown so the
 * processor leaves its token where it was and tries the event again; the
 * {@code IdempotencyKey} comes from the event, so every attempt sends the same
 * one and Stripe returns the original session rather than opening a second
 * charge. A permanent failure is logged and swallowed: retrying it would block
 * this processing group's queue forever over an event that will never succeed.
 *
 * <p><strong>The gap that leaves:</strong> a permanently failed
 * {@code createDeposit} leaves the deposit stuck in {@code PENDING} with no way
 * to fail it. The only path to {@code FAILED} runs through
 * {@code ApplyProviderDepositUpdateCommand}, which demands a registered provider
 * reference and a {@code providerEventId}, and neither exists when the charge was
 * never created. Closing it needs a new command on the aggregate; it is recorded
 * under «Decisiones pendientes» in the contract.
 *
 * <p><strong>On replay:</strong> resetting the {@code deposit-charge-creation}
 * token calls Stripe again. Recent events are harmless — the idempotency key
 * makes Stripe return the original session, and
 * {@code RegisterDepositProviderReferenceCommand} is idempotent on exact
 * repetition — but Stripe's deduplication window is 24 hours, so replaying older
 * history <em>would</em> open new Checkout Sessions. Not something to do casually.
 */
@Component
@ProcessingGroup("deposit-charge-creation")
public class DepositChargeCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DepositChargeCreator.class);

    private final PaymentProvider paymentProvider;
    private final CommandGateway commandGateway;

    public DepositChargeCreator(PaymentProvider paymentProvider, CommandGateway commandGateway) {
        this.paymentProvider = paymentProvider;
        this.commandGateway = commandGateway;
    }

    @EventHandler
    public void on(DepositInitiatedEvent event) {
        DepositId depositId = DepositId.of(event.depositId());
        Provider provider = Provider.valueOf(event.provider());
        Money amount = new Money(event.amountMinor(), Currency.valueOf(event.currency()));
        IdempotencyKey idempotencyKey = new IdempotencyKey(event.idempotencyKey());

        ProviderDepositCreated created;
        try {
            created = paymentProvider.createDeposit(idempotencyKey, depositId, amount, event.description());
        } catch (PaymentProviderException exception) {
            // By the marker interface, never by concrete type or message text: the
            // port's whole point is that callers decide to retry without knowing
            // which provider produced the failure.
            if (exception instanceof RetryablePaymentProviderException) {
                LOGGER.warn("Transient failure creating the charge, letting the processor retry: "
                        + "depositId={}, provider={}", depositId, provider);
                throw exception;
            }
            LOGGER.error("Charge creation failed for good, deposit stays PENDING: depositId={}, provider={}",
                    depositId, provider, exception);
            return;
        } catch (IllegalStateException exception) {
            // Missing or disabled configuration — our deployment error, not an
            // answer from Stripe. Retrying it would spin until someone fixes the
            // environment, so it is reported and left alone. See
            // docs/guides/stripe-configuration.md.
            LOGGER.error("Charge creation could not even be attempted, deposit stays PENDING: "
                    + "depositId={}, provider={}", depositId, provider, exception);
            return;
        }

        commandGateway.sendAndWait(new RegisterDepositProviderReferenceCommand(
                depositId,
                provider,
                created.providerDepositId(),
                Instant.now(),
                created.actionUrl()));
    }
}
