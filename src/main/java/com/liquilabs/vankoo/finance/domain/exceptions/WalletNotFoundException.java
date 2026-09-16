package com.liquilabs.vankoo.finance.domain.exceptions;

/**
 * A command addressed a wallet that was never opened. {@code Wallet} is created
 * lazily on the first successful deposit, so this is the normal answer for an
 * investor who has not topped up in that currency yet — not a bug.
 */
public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(String walletId) {
        super("No wallet " + walletId + " has been opened");
    }
}
