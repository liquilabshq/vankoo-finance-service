package com.liquilabs.vankoo.finance.interfaces.messaging.eventhandlers;

import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AuctionId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.services.AuctionFundingCreditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuctionLifecycleEventHandlerTest {

    private static final String AUCTION_ID = "20000000-0000-4000-8000-000000000001";
    private static final String MYPE_ID = "30000000-0000-4000-8000-000000000001";

    @Mock
    private AuctionFundingCreditService creditService;

    private AuctionLifecycleEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AuctionLifecycleEventHandler(creditService);
    }

    @Test
    void fullyFunded_creditsTheMypeAdvanceInMinorUnits() {
        handler.accept(record("AuctionFullyFunded", """
                {"invoiceId":"inv-1","mypeId":"%s","currency":"PEN","fundedAmount":9850.00,"mypeAdvance":9800.55}
                """.formatted(MYPE_ID)));

        verify(creditService).creditAdvance(
                AuctionId.of(AUCTION_ID), AccountId.of(MYPE_ID), new Money(980_055L, Currency.PEN));
    }

    @Test
    void fullyFundedWithIntegerAdvance_isReadExactly() {
        handler.accept(record("AuctionFullyFunded", """
                {"mypeId":"%s","currency":"USD","mypeAdvance":1200}
                """.formatted(MYPE_ID)));

        verify(creditService).creditAdvance(
                AuctionId.of(AUCTION_ID), AccountId.of(MYPE_ID), new Money(120_000L, Currency.USD));
    }

    @Test
    void otherEventTypes_areIgnored() {
        handler.accept(record("PartitionAdded", """
                {"partitionId":"p-1","addedAmount":500.00,"currentFunding":500.00}
                """));
        handler.accept(record("AuctionPublished", "{}"));

        verifyNoInteractions(creditService);
    }

    @Test
    void fullyFundedWithEmptyData_publishedBeforeTheFieldsExisted_isSkipped() {
        handler.accept(record("AuctionFullyFunded", "{}"));

        verifyNoInteractions(creditService);
    }

    @Test
    void unusableValues_areSkippedNotRetried() {
        handler.accept(record("AuctionFullyFunded", """
                {"mypeId":"not-a-uuid","currency":"PEN","mypeAdvance":10.00}
                """));
        handler.accept(record("AuctionFullyFunded", """
                {"mypeId":"%s","currency":"PEN","mypeAdvance":10.001}
                """.formatted(MYPE_ID)));
        handler.accept(record("AuctionFullyFunded", """
                {"mypeId":"%s","currency":"MXN","mypeAdvance":10.00}
                """.formatted(MYPE_ID)));

        verifyNoInteractions(creditService);
    }

    @Test
    void notJson_isSkipped() {
        handler.accept(MessageBuilder.withPayload("not json".getBytes(StandardCharsets.UTF_8)).build());

        verifyNoInteractions(creditService);
    }

    @Test
    void unknownSchemaVersion_isSkipped() {
        String json = """
                {"eventId":"e-1","eventType":"AuctionFullyFunded","schemaVersion":2,"aggregateId":"%s",
                 "sequence":4,"occurredAt":"2026-11-23T18:00:00Z","data":{"mypeId":"%s","currency":"PEN","mypeAdvance":1}}
                """.formatted(AUCTION_ID, MYPE_ID);
        handler.accept(MessageBuilder.withPayload(json.getBytes(StandardCharsets.UTF_8)).build());

        verifyNoInteractions(creditService);
    }

    private static org.springframework.messaging.Message<byte[]> record(String eventType, String data) {
        String json = """
                {"eventId":"10000000-0000-4000-8000-000000000004","eventType":"%s","schemaVersion":1,
                 "aggregateId":"%s","sequence":4,"occurredAt":"2026-11-23T18:00:00Z","data":%s}
                """.formatted(eventType, AUCTION_ID, data.strip());
        return MessageBuilder.withPayload(json.getBytes(StandardCharsets.UTF_8)).build();
    }
}
