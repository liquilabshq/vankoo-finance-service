package com.liquilabs.vankoo.finance.interfaces.messaging.eventhandlers;

import com.liquilabs.vankoo.finance.domain.model.queries.GetWalletBalanceQuery;
import com.liquilabs.vankoo.finance.domain.model.queries.ListWalletMovementsQuery;
import com.liquilabs.vankoo.finance.domain.model.queries.WalletBalance;
import com.liquilabs.vankoo.finance.domain.model.queries.WalletMovementPage;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.WalletMovementKind;
import com.liquilabs.vankoo.finance.domain.services.WalletQueryService;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The money path end to end inside Finance, against the real Postgres, Axon
 * Server and Kafka the rest of the {@code @SpringBootTest}s use: an
 * {@code AuctionFullyFunded} reaches the MYPE's wallet as one {@code ADELANTO},
 * and a repeat of the fact — the same record redelivered, or the same auction
 * under a new {@code eventId} — never pays twice.
 */
@SpringBootTest
class AuctionFundingCreditIntegrationTest {

    private static final Duration PROJECTION_TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private AuctionLifecycleEventHandler handler;

    @Autowired
    private WalletQueryService walletQueryService;

    @Value("${spring.cloud.stream.kafka.binder.brokers}")
    private String brokers;

    @Test
    void theSameFundedAuctionCreditsTheMypeOnce() throws InterruptedException {
        String auctionId = UUID.randomUUID().toString();
        AccountId mype = AccountId.of(UUID.randomUUID().toString());
        String event = fullyFunded(UUID.randomUUID().toString(), auctionId, mype, "4321.50");

        handler.accept(message(event));
        handler.accept(message(event));
        handler.accept(message(fullyFunded(UUID.randomUUID().toString(), auctionId, mype, "4321.50")));

        WalletBalance balance = await(() -> walletQueryService.getWalletBalance(
                new GetWalletBalanceQuery(mype, Currency.PEN)));
        // Give a would-be second credit time to show up before asserting it did not.
        Thread.sleep(2_000);

        assertThat(walletQueryService.getWalletBalance(new GetWalletBalanceQuery(mype, Currency.PEN)))
                .get().extracting(b -> b.balance().amountMinor()).isEqualTo(432_150L);
        WalletMovementPage movements = walletQueryService.listWalletMovements(
                new ListWalletMovementsQuery(mype, Currency.PEN, 0, 20));
        assertThat(movements.items()).hasSize(1);
        assertThat(movements.items().getFirst().type()).isEqualTo(WalletMovementKind.ADELANTO);
        assertThat(movements.items().getFirst().sourceAuctionId().toString()).isEqualTo(auctionId);
        assertThat(balance.accountId()).isEqualTo(mype);
    }

    @Test
    void aFundedAuctionPublishedOnTheTopicReachesTheMypeWallet() {
        String auctionId = UUID.randomUUID().toString();
        AccountId mype = AccountId.of(UUID.randomUUID().toString());

        try (var producer = new KafkaProducer<String, byte[]>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class))) {
            producer.send(new ProducerRecord<>("investment.auction-lifecycle.v1", auctionId,
                    fullyFunded(UUID.randomUUID().toString(), auctionId, mype, "150.00")
                            .getBytes(StandardCharsets.UTF_8)));
            producer.flush();
        }

        WalletBalance balance = await(() -> walletQueryService.getWalletBalance(
                new GetWalletBalanceQuery(mype, Currency.PEN)));
        assertThat(balance.balance().amountMinor()).isEqualTo(15_000L);
    }

    private static <T> T await(Supplier<Optional<T>> probe) {
        Instant deadline = Instant.now().plus(PROJECTION_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Optional<T> value = probe.get();
            if (value.isPresent()) {
                return value.get();
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
        throw new AssertionError("Nothing was projected within " + PROJECTION_TIMEOUT);
    }

    private static org.springframework.messaging.Message<byte[]> message(String json) {
        return MessageBuilder.withPayload(json.getBytes(StandardCharsets.UTF_8)).build();
    }

    private static String fullyFunded(String eventId, String auctionId, AccountId mype, String advance) {
        return """
                {"eventId":"%s","eventType":"AuctionFullyFunded","schemaVersion":1,"aggregateId":"%s",
                 "sequence":5,"occurredAt":"2026-11-23T18:00:00Z",
                 "data":{"invoiceId":"%s","mypeId":"%s","currency":"PEN","fundedAmount":%s,"mypeAdvance":%s}}
                """.formatted(eventId, auctionId, UUID.randomUUID(), mype, advance, advance);
    }
}
