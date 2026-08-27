package com.liquilabs.vankoo.finance.application.internal.commandservices;

import com.liquilabs.vankoo.finance.domain.exceptions.IdempotencyKeyConflictException;
import com.liquilabs.vankoo.finance.domain.model.commands.InitiateDepositCommand;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DepositId;
import com.liquilabs.vankoo.finance.domain.services.DepositCommandService;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.DepositCommandIdempotency;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories.DepositCommandIdempotencyRepository;
import com.fasterxml.uuid.Generators;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Implements {@link DepositCommandService}. What this class adds over a plain
 * {@code CommandGateway.sendAndWait} call is the {@code Idempotency-Key}
 * barrier: the same {@code (accountId, idempotencyKey)} never dispatches
 * {@link InitiateDepositCommand} twice.
 *
 * <p><b>Insert first, dispatch second</b> — same principle as
 * {@code WalletCreditedDepositRepository.insertIfAbsent}, applied to the
 * opposite order. Dispatching first and recording the key second would let
 * two truly concurrent requests carrying the same key both see "no row yet"
 * and both dispatch, creating two {@code Deposit} aggregates for one logical
 * request. Only the request whose {@code INSERT ... ON CONFLICT DO NOTHING}
 * actually inserts a row is allowed to call {@code commandGateway}; the loser
 * re-reads and either replays the existing {@code depositId} or rejects the
 * conflict — it never dispatches.
 *
 * <p><b>{@code @Transactional} spans the {@code CommandGateway} call on
 * purpose</b>, unlike {@code WalletCreditor}. Without it, {@code insertIfAbsent}
 * would commit on its own before {@code sendAndWait} returns; if the command
 * then threw (e.g. {@code InvalidDepositAmountException}), the idempotency row
 * would be left permanently pointing at a {@code depositId} for which no
 * {@code Deposit} was ever created, poisoning that key forever. Widening the
 * transaction so the insert joins it means a failed dispatch rolls the
 * reservation back too.
 */
@Service
public class DepositCommandServiceImpl implements DepositCommandService {

    private final DepositCommandIdempotencyRepository idempotencyRepository;
    private final CommandGateway commandGateway;

    public DepositCommandServiceImpl(DepositCommandIdempotencyRepository idempotencyRepository,
                                      CommandGateway commandGateway) {
        this.idempotencyRepository = idempotencyRepository;
        this.commandGateway = commandGateway;
    }

    @Override
    @Transactional
    public DepositId handle(InitiateDepositCommand command) {
        String requestHash = RequestDigest.sha256Hex(canonicalize(command));

        int inserted = idempotencyRepository.insertIfAbsent(
                Generators.timeBasedEpochGenerator().generate(),
                command.accountId().value(),
                command.idempotencyKey().value(),
                command.depositId().value(),
                requestHash,
                Instant.now());

        if (inserted == 0) {
            return resolveExisting(command, requestHash);
        }

        // No I/O in Deposit's @CommandHandler constructor — it only validates
        // amountMinor > 0 and applies an event — so there is no latency reason
        // to fire-and-forget, and sendAndWait is what lets a rejected command
        // roll the reservation above back instead of leaving it dangling.
        commandGateway.sendAndWait(command);
        return command.depositId();
    }

    /**
     * This key was already admitted. An exact repeat of the request returns
     * the {@code depositId} it returned the first time, without dispatching
     * anything again; a repeat under different content is a conflict, not a
     * silent overwrite.
     */
    private DepositId resolveExisting(InitiateDepositCommand command, String requestHash) {
        DepositCommandIdempotency existing = idempotencyRepository
                .findByAccountIdAndIdempotencyKey(command.accountId(), command.idempotencyKey().value())
                .orElseThrow(() -> new IllegalStateException(
                        "insertIfAbsent reported a conflict but no row was found for accountId="
                                + command.accountId() + ", idempotencyKey=" + command.idempotencyKey().value()));

        if (existing.getRequestHash().equals(requestHash)) {
            return existing.getDepositId();
        }

        throw new IdempotencyKeyConflictException(
                "Idempotency-Key " + command.idempotencyKey().value() + " for account " + command.accountId()
                        + " was already used with different request content");
    }

    /** Only the fields that make two requests "the same" for this barrier. */
    private static String canonicalize(InitiateDepositCommand command) {
        return command.accountId().value() + "|"
                + command.amount().amountMinor() + "|"
                + command.amount().currency() + "|"
                + command.provider() + "|"
                + (command.description() == null ? "" : command.description());
    }
}
