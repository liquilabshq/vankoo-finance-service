package com.liquilabs.vankoo.finance.application.internal.commandservices;

import com.liquilabs.vankoo.finance.domain.exceptions.CommandRejectedException;
import com.liquilabs.vankoo.finance.domain.exceptions.IdempotencyKeyConflictException;
import com.liquilabs.vankoo.finance.domain.model.commands.DebitWalletCommand;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.AccountId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.DebitId;
import com.liquilabs.vankoo.finance.domain.model.valueobjects.IdempotencyKey;
import com.liquilabs.vankoo.finance.domain.services.WalletCommandService;
import com.liquilabs.vankoo.finance.infrastructure.eventstore.axon.CommandRejection;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.entities.WalletDebitCommandIdempotency;
import com.liquilabs.vankoo.finance.infrastructure.persistence.jpa.repositories.WalletDebitCommandIdempotencyRepository;
import com.fasterxml.uuid.Generators;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Implements {@link WalletCommandService}: the {@code Idempotency-Key} barrier
 * in front of {@link DebitWalletCommand}, a line-for-line twin of
 * {@link DepositCommandServiceImpl} — read that class for why it is
 * <b>insert first, dispatch second</b> and why {@code @Transactional} spans
 * the {@code CommandGateway} call. Both reasons matter more here than for
 * deposits: the race the barrier closes is a double debit, and the rollback
 * is what lets an investor whose debit was refused for insufficient balance
 * top up and retry with the very same key.
 *
 * <p>What this class adds over the deposit one is the unwrapping in
 * {@link #dispatch}: the aggregate's rejection does not come back as the
 * domain exception it threw (see {@code CommandRejectionHandlerInterceptor}),
 * so it is translated here into {@link CommandRejectedException}, keyed by
 * {@code code}, before it reaches the REST layer.
 */
@Service
public class WalletCommandServiceImpl implements WalletCommandService {

    private final WalletDebitCommandIdempotencyRepository idempotencyRepository;
    private final CommandGateway commandGateway;

    public WalletCommandServiceImpl(WalletDebitCommandIdempotencyRepository idempotencyRepository,
                                    CommandGateway commandGateway) {
        this.idempotencyRepository = idempotencyRepository;
        this.commandGateway = commandGateway;
    }

    @Override
    @Transactional
    public DebitId handle(DebitWalletCommand command, AccountId accountId, IdempotencyKey idempotencyKey) {
        String requestHash = RequestDigest.sha256Hex(canonicalize(command, accountId));

        int inserted = idempotencyRepository.insertIfAbsent(
                Generators.timeBasedEpochGenerator().generate(),
                accountId.value(),
                idempotencyKey.value(),
                command.debitId().value(),
                requestHash,
                Instant.now());

        if (inserted == 0) {
            return resolveExisting(accountId, idempotencyKey, requestHash);
        }

        dispatch(command);
        return command.debitId();
    }

    /**
     * {@code sendAndWait} on purpose: the handler only compares the amount to
     * the balance and applies one event, and a synchronous answer is what lets
     * a refusal roll the reservation above back and reach the client as a
     * {@code 409}/{@code 404} instead of a debit that silently never happened.
     */
    private void dispatch(DebitWalletCommand command) {
        try {
            commandGateway.sendAndWait(command);
        } catch (CommandExecutionException exception) {
            throw exception.<Object>getDetails()
                    .filter(CommandRejection.class::isInstance)
                    .map(CommandRejection.class::cast)
                    .map(rejection -> (RuntimeException) new CommandRejectedException(
                            rejection.code(), rejection.message()))
                    .orElse(exception);
        }
    }

    /**
     * This key was already admitted. An exact repeat returns the
     * {@code debitId} it returned the first time, without dispatching again;
     * a repeat under different content is a conflict, not a silent overwrite.
     */
    private DebitId resolveExisting(AccountId accountId, IdempotencyKey idempotencyKey, String requestHash) {
        WalletDebitCommandIdempotency existing = idempotencyRepository
                .findByAccountIdAndIdempotencyKey(accountId, idempotencyKey.value())
                .orElseThrow(() -> new IllegalStateException(
                        "insertIfAbsent reported a conflict but no row was found for accountId="
                                + accountId + ", idempotencyKey=" + idempotencyKey.value()));

        if (existing.getRequestHash().equals(requestHash)) {
            return existing.getDebitId();
        }

        throw new IdempotencyKeyConflictException(
                "Idempotency-Key " + idempotencyKey.value() + " for account " + accountId
                        + " was already used with different request content");
    }

    /**
     * Only the fields that make two requests "the same" for this barrier.
     * {@code debitId} is deliberately not one of them: it is minted per HTTP
     * request, so including it would make every retry look different.
     */
    private static String canonicalize(DebitWalletCommand command, AccountId accountId) {
        return accountId.value() + "|"
                + command.amount().currency() + "|"
                + command.amount().amountMinor() + "|"
                + command.reason();
    }
}
