package com.liquilabs.vankoo.finance.domain.exceptions;

/**
 * A provider reference operation does not match the deposit's current state
 * (invariants 4, 5 and 6):
 *
 * <ul>
 *   <li>registering a provider reference different from the one already registered;</li>
 *   <li>applying a provider update whose {@code provider}/{@code providerDepositId}
 *       does not match what is registered;</li>
 *   <li>applying a provider update before any reference has been registered at
 *       all — defensive: the adapter is supposed to park this case instead of
 *       ever sending the command (invariant 6), so this only fires if that
 *       upstream rule was not honored.</li>
 * </ul>
 */
public class ProviderReferenceMismatchException extends RuntimeException {

    public ProviderReferenceMismatchException(String message) {
        super(message);
    }
}
