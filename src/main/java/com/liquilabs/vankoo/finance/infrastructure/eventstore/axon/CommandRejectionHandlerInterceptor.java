package com.liquilabs.vankoo.finance.infrastructure.eventstore.axon;

import com.liquilabs.vankoo.finance.domain.exceptions.InsufficientBalanceException;
import com.liquilabs.vankoo.finance.domain.exceptions.InvalidCreditAmountException;
import com.liquilabs.vankoo.finance.domain.exceptions.InvalidDebitAmountException;
import com.liquilabs.vankoo.finance.domain.exceptions.InvalidDepositAmountException;
import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.AggregateNotFoundException;

/**
 * Turns the domain exceptions a {@code @CommandHandler} throws into something
 * the dispatcher can still recognise after the round trip through Axon Server.
 *
 * <p><strong>Why this exists.</strong> The command bus is
 * {@code AxonServerCommandBus}: every command, even one handled in this same
 * JVM, goes out to Axon Server and comes back. When the handler throws, the
 * connector serializes only an error code and a message, and the dispatcher's
 * {@code sendAndWait} receives a {@link CommandExecutionException} whose cause
 * is a generic {@code AxonServerRemoteCommandHandlingException} — a
 * {@code catch (InsufficientBalanceException e)} around {@code sendAndWait}
 * never matches. The one thing the connector does carry across intact is the
 * {@code details} of a {@code CommandExecutionException} thrown on the handler
 * side, which is the mechanism Axon itself recommends in its own warning
 * («wrap the exception in a CommandExecutionException with provided details»).
 *
 * <p>So this interceptor sits on the handler side of the bus, lets the
 * aggregate keep throwing plain domain exceptions (its tests and its idiom
 * stay untouched), and rewraps the ones it knows with a
 * {@link CommandRejection} as details. {@code WalletCommandServiceImpl}
 * unwraps it on the other side. Anything it does not recognise passes through
 * unchanged.
 *
 * <p>Registered explicitly by {@code CommandBusConfiguration} rather than
 * relying on bean auto-detection.
 */
public class CommandRejectionHandlerInterceptor implements MessageHandlerInterceptor<CommandMessage<?>> {

    @Override
    public Object handle(@Nonnull UnitOfWork<? extends CommandMessage<?>> unitOfWork,
                         @Nonnull InterceptorChain interceptorChain) throws Exception {
        try {
            return interceptorChain.proceed();
        } catch (InsufficientBalanceException exception) {
            throw reject(CommandRejection.INSUFFICIENT_BALANCE, exception);
        } catch (AggregateNotFoundException exception) {
            // The only aggregate loaded by an externally-minted, derived id is
            // Wallet (WalletId.derive) — Deposit ids are minted on creation, so
            // a missing Deposit here would be a bug, not a client error.
            throw reject(CommandRejection.WALLET_NOT_FOUND, exception);
        } catch (InvalidDebitAmountException | InvalidCreditAmountException | InvalidDepositAmountException exception) {
            throw reject(CommandRejection.INVALID_REQUEST, exception);
        }
    }

    private static CommandExecutionException reject(String code, RuntimeException cause) {
        return new CommandExecutionException(cause.getMessage(), cause, new CommandRejection(code, cause.getMessage()));
    }
}
