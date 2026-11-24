package com.liquilabs.vankoo.finance.interfaces.messaging.eventhandlers;

import com.liquilabs.vankoo.finance.domain.exceptions.UnsupportedCurrencyException;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AuctionId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.services.AuctionFundingCreditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * Finance's first consumer of another bounded context: Investment's
 * {@code investment.auction-lifecycle.v1}. Only {@code AuctionFullyFunded}
 * matters here — the moment the MYPE is owed its advance — and every other
 * type is acknowledged and ignored, as the contract asks of its consumers.
 *
 * <p>Bound by name as {@code auctionLifecycle-in-0} (see
 * {@code spring.cloud.function.definition}). It only translates the wire into
 * Finance's vocabulary and hands over to {@link AuctionFundingCreditService};
 * the decision of whether to credit lives there.
 *
 * <p><strong>Which account is the MYPE's.</strong> {@code data.mypeId} is the
 * MYPE's IAM user id — the {@code sub} of its JWT — because that is what
 * Invoicing stamps on the invoice and Investment copies onto the auction. It
 * is also what Finance already uses as {@code accountId} for the investor (the
 * gateway's {@code X-User-Id}, checked by {@code CallerOwnershipInterceptor}).
 * So the MYPE's wallet is simply {@code WalletId.derive(mypeId, currency)},
 * and the web reads it with its own {@code sub}, with no mapping table.
 *
 * <p>Two kinds of failure, treated differently:
 * <ul>
 *   <li>A message this consumer can never use — not JSON, a {@code mypeId}
 *       that is not a UUID, an amount with more than two decimals — is
 *       logged and acknowledged. Retrying it would fail the same way.</li>
 *   <li>Anything else (database, Axon Server) is thrown, so the binder
 *       retries and, past its attempts, parks the record in the DLQ.</li>
 * </ul>
 *
 * <p>An {@code AuctionFullyFunded} published before Investment started
 * carrying {@code mypeId} and {@code mypeAdvance} has an empty {@code data}
 * and cannot be paid from the event alone: it is logged as a warning and
 * skipped, never guessed.
 */
@Component("auctionLifecycle")
public class AuctionLifecycleEventHandler implements Consumer<Message<byte[]>> {

    static final String AUCTION_FULLY_FUNDED = "AuctionFullyFunded";
    private static final int SCHEMA_VERSION = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionLifecycleEventHandler.class);

    private final AuctionFundingCreditService creditService;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public AuctionLifecycleEventHandler(AuctionFundingCreditService creditService) {
        this.creditService = creditService;
    }

    @Override
    public void accept(Message<byte[]> message) {
        JsonNode envelope;
        try {
            envelope = jsonMapper.readTree(message.getPayload());
        } catch (RuntimeException exception) {
            LOGGER.error("Skipping auction lifecycle record that is not JSON: {}", exception.getMessage());
            return;
        }

        String eventType = envelope.path("eventType").asString("");
        if (!AUCTION_FULLY_FUNDED.equals(eventType)) {
            return;
        }
        String eventId = envelope.path("eventId").asString("");
        if (envelope.path("schemaVersion").asInt(-1) != SCHEMA_VERSION) {
            LOGGER.error("Skipping {} {} with unsupported schemaVersion {}",
                    eventType, eventId, envelope.path("schemaVersion"));
            return;
        }

        FundedAuction funded;
        try {
            funded = FundedAuction.from(envelope);
        } catch (IllegalArgumentException | ArithmeticException | UnsupportedCurrencyException exception) {
            LOGGER.error("Skipping {} {}: {}", eventType, eventId, exception.getMessage());
            return;
        }
        if (funded == null) {
            LOGGER.warn("Skipping {} {} for auction {}: it carries no mypeId/mypeAdvance "
                            + "(published before Investment added them), so the MYPE cannot be paid from it",
                    eventType, eventId, envelope.path("aggregateId").asString(""));
            return;
        }

        creditService.creditAdvance(funded.auctionId(), funded.mypeAccountId(), funded.advance());
    }

    /** The three facts the credit needs, read off the wire. */
    record FundedAuction(AuctionId auctionId, AccountId mypeAccountId, Money advance) {

        /** {@code null} when the optional fields are absent; throws when they are present but unusable. */
        static FundedAuction from(JsonNode envelope) {
            JsonNode data = envelope.path("data");
            if (!data.hasNonNull("mypeId") || !data.hasNonNull("mypeAdvance") || !data.hasNonNull("currency")) {
                return null;
            }
            AuctionId auctionId = AuctionId.of(envelope.path("aggregateId").asString(""));
            AccountId mypeAccountId = AccountId.of(data.get("mypeId").asString());
            Currency currency = Currency.fromIsoCode(data.get("currency").asString());
            BigDecimal advance = data.get("mypeAdvance").decimalValue();
            long amountMinor = advance.movePointRight(2).longValueExact();
            return new FundedAuction(auctionId, mypeAccountId, new Money(amountMinor, currency));
        }
    }
}
