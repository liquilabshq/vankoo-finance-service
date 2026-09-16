package com.liquilabs.vankoo.finance.infrastructure.eventstore.axon;

import com.liquilabs.vankoo.finance.domain.exceptions.InsufficientBalanceException;
import com.liquilabs.vankoo.finance.domain.exceptions.InvalidDebitAmountException;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plain unit test, no Axon Server: the interceptor is called with a mocked
 * chain, which is all it touches.
 */
class CommandRejectionHandlerInterceptorTest {

    private final CommandRejectionHandlerInterceptor interceptor = new CommandRejectionHandlerInterceptor();

    @SuppressWarnings("unchecked")
    private final UnitOfWork<CommandMessage<?>> unitOfWork = mock(UnitOfWork.class);

    @Test
    void successfulHandling_returnsTheChainResult() throws Exception {
        InterceptorChain chain = mock(InterceptorChain.class);
        when(chain.proceed()).thenReturn("ok");

        assertEquals("ok", interceptor.handle(unitOfWork, chain));
    }

    @Test
    void insufficientBalance_isWrappedWithItsCodeAsDetails() throws Exception {
        InterceptorChain chain = mock(InterceptorChain.class);
        InsufficientBalanceException cause = new InsufficientBalanceException("w-1", 100L, 5_000L);
        when(chain.proceed()).thenThrow(cause);

        CommandExecutionException thrown = assertThrows(CommandExecutionException.class,
                () -> interceptor.handle(unitOfWork, chain));

        Optional<CommandRejection> details = thrown.getDetails();
        assertEquals(CommandRejection.INSUFFICIENT_BALANCE, details.orElseThrow().code());
        assertEquals(cause.getMessage(), details.orElseThrow().message());
        assertSame(cause, thrown.getCause());
    }

    @Test
    void aggregateNotFound_isWrappedAsWalletNotFound() throws Exception {
        InterceptorChain chain = mock(InterceptorChain.class);
        when(chain.proceed()).thenThrow(new AggregateNotFoundException("w-1", "not found"));

        CommandExecutionException thrown = assertThrows(CommandExecutionException.class,
                () -> interceptor.handle(unitOfWork, chain));

        assertEquals(CommandRejection.WALLET_NOT_FOUND, thrown.<CommandRejection>getDetails().orElseThrow().code());
    }

    @Test
    void invalidAmount_isWrappedAsInvalidRequest() throws Exception {
        InterceptorChain chain = mock(InterceptorChain.class);
        when(chain.proceed()).thenThrow(new InvalidDebitAmountException(0L));

        CommandExecutionException thrown = assertThrows(CommandExecutionException.class,
                () -> interceptor.handle(unitOfWork, chain));

        assertEquals(CommandRejection.INVALID_REQUEST, thrown.<CommandRejection>getDetails().orElseThrow().code());
    }

    @Test
    void unknownException_passesThroughUntouched() throws Exception {
        InterceptorChain chain = mock(InterceptorChain.class);
        IllegalStateException unknown = new IllegalStateException("something else");
        when(chain.proceed()).thenThrow(unknown);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> interceptor.handle(unitOfWork, chain));

        assertSame(unknown, thrown);
    }
}
