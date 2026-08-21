package com.liquilabs.vankoo.finance.application.internal.queryservices;

import com.liquilabs.vankoo.finance.domain.model.events.DepositActionRequiredEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositCancelledEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositFailedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositInitiatedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositProcessingStartedEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositProviderReferenceRegisteredEvent;
import com.liquilabs.vankoo.finance.domain.model.events.DepositSucceededEvent;
import com.liquilabs.vankoo.finance.domain.model.queries.DepositSummary;
import com.liquilabs.vankoo.finance.domain.model.queries.DepositSummaryPage;
import com.liquilabs.vankoo.finance.domain.model.queries.GetDepositByIdQuery;
import com.liquilabs.vankoo.finance.domain.model.queries.ListDepositsByAccountQuery;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.FailureReason;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.Provider;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.ProviderDepositId;
import com.liquilabs.vankoo.finance.domain.services.DepositQueryService;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.DepositViewEntity;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories.DepositViewRepository;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.Timestamp;
import org.axonframework.messaging.annotation.MessageIdentifier;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The Query Model for {@code Deposit}: projection and query handlers together,
 * as the guide's figure 6-7 draws them — one unit, not split across packages.
 *
 * <p>{@code @ProcessingGroup} is declared explicitly. Its default is the
 * handler's package name, and that name is what the token store remembers:
 * without this annotation, moving this class would start a fresh processor
 * with no token and force a full replay.
 */
@Component
@ProcessingGroup("deposit-read-model")
public class DepositProjection implements DepositQueryService {

    private final DepositViewRepository repository;

    public DepositProjection(DepositViewRepository repository) {
        this.repository = repository;
    }

    /** The only event that creates a row. */
    @EventHandler
    public void on(DepositInitiatedEvent event, @MessageIdentifier String eventId, @Timestamp Instant occurredAt) {
        DepositId depositId = DepositId.of(event.depositId());
        if (repository.existsByDepositId(depositId)) {
            return;
        }
        repository.save(DepositViewEntity.initiate(
                depositId,
                AccountId.of(event.accountId()),
                event.amountMinor(),
                Currency.valueOf(event.currency()),
                Provider.valueOf(event.provider()),
                event.description(),
                eventId,
                occurredAt));
    }

    /**
     * Does not touch status, amount, account, currency or provider: this
     * event carries none of them.
     */
    @EventHandler
    public void on(DepositProviderReferenceRegisteredEvent event,
                    @MessageIdentifier String eventId, @Timestamp Instant occurredAt) {
        apply(event.depositId(), eventId, row -> row.registerProviderReference(
                new ProviderDepositId(event.providerDepositId()), event.actionUrl(), eventId, occurredAt));
    }

    @EventHandler
    public void on(DepositActionRequiredEvent event, @MessageIdentifier String eventId, @Timestamp Instant occurredAt) {
        apply(event.depositId(), eventId, row -> row.markActionRequired(eventId, occurredAt));
    }

    @EventHandler
    public void on(DepositProcessingStartedEvent event, @MessageIdentifier String eventId, @Timestamp Instant occurredAt) {
        apply(event.depositId(), eventId, row -> row.markProcessing(eventId, occurredAt));
    }

    @EventHandler
    public void on(DepositSucceededEvent event, @MessageIdentifier String eventId, @Timestamp Instant occurredAt) {
        apply(event.depositId(), eventId, row -> row.markSucceeded(eventId, occurredAt));
    }

    @EventHandler
    public void on(DepositFailedEvent event, @MessageIdentifier String eventId, @Timestamp Instant occurredAt) {
        apply(event.depositId(), eventId,
                row -> row.markFailed(FailureReason.valueOf(event.failureReason()), eventId, occurredAt));
    }

    @EventHandler
    public void on(DepositCancelledEvent event, @MessageIdentifier String eventId, @Timestamp Instant occurredAt) {
        apply(event.depositId(), eventId,
                row -> row.markCancelled(event.cancellationReason(), eventId, occurredAt));
    }

    /**
     * Loads the row, skips if {@code eventId} was already applied (replay or
     * redelivery of the tail event), otherwise runs the mutation and saves.
     *
     * <p>{@code DepositInitiatedEvent} always precedes every other event for a
     * given aggregate, so a missing row here is a projection bug, not a normal
     * branch to swallow.
     */
    private void apply(String rawDepositId, String eventId, Consumer<DepositViewEntity> mutation) {
        DepositId depositId = DepositId.of(rawDepositId);
        DepositViewEntity row = repository.findByDepositId(depositId)
                .orElseThrow(() -> new IllegalStateException(
                        "No deposit_view row for depositId=" + rawDepositId + " when applying event " + eventId));
        if (row.alreadyApplied(eventId)) {
            return;
        }
        mutation.accept(row);
        repository.save(row);
    }

    @Override
    @QueryHandler
    public Optional<DepositSummary> getDepositById(GetDepositByIdQuery query) {
        return repository.findByDepositId(query.depositId()).map(this::toSummary);
    }

    @Override
    @QueryHandler
    public DepositSummaryPage listDepositsByAccount(ListDepositsByAccountQuery query) {
        Pageable pageable = PageRequest.of(query.pageNumber(), query.pageSize());
        Page<DepositViewEntity> page = query.status() == null
                ? repository.findByAccountIdOrderByCreatedAtDesc(query.accountId(), pageable)
                : repository.findByAccountIdAndStatusOrderByCreatedAtDesc(query.accountId(), query.status(), pageable);

        return new DepositSummaryPage(
                page.getContent().stream().map(this::toSummary).toList(),
                query.pageNumber(),
                query.pageSize(),
                page.getTotalElements());
    }

    private DepositSummary toSummary(DepositViewEntity entity) {
        return new DepositSummary(
                entity.getDepositId(),
                entity.getAccountId(),
                new Money(entity.getAmountMinor(), entity.getCurrency()),
                entity.getProvider(),
                entity.getProviderDepositId(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getActionUrl(),
                entity.getFailureReason(),
                entity.getCancellationReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
