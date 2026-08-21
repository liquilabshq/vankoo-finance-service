package com.liquilabs.vankoo.finance.domain.model.valueobjects;

import com.fasterxml.uuid.Generators;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Identity of a wallet. Unlike {@link DepositId}, it is never generated
 * randomly: it is derived deterministically from {@code (accountId, currency)}
 * so that "does this wallet exist" never needs a translation table — the id
 * is computed, not looked up.
 */
public record WalletId(UUID value) {

    /**
     * Fixed namespace for every derivation. Chosen once, hardcoded forever:
     * changing it would silently re-derive every existing WalletId and orphan
     * every wallet already in the event store.
     */
    private static final UUID NAMESPACE = UUID.fromString("36ee4e84-95e2-40f6-80bd-45c6b073fbb7");

    public WalletId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }

    /**
     * Deterministic UUIDv5 (name-based, SHA-1) over {@code accountId:currency}.
     * Same inputs always produce the same id.
     */
    public static WalletId derive(AccountId accountId, Currency currency) {
        if (accountId == null || currency == null) {
            throw new IllegalArgumentException("accountId and currency must not be null");
        }
        try {
            String name = accountId.value() + ":" + currency.name();
            UUID derived = Generators.nameBasedGenerator(NAMESPACE, MessageDigest.getInstance("SHA-1"))
                    .generate(name);
            return new WalletId(derived);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 must be available on any JVM", exception);
        }
    }

    public static WalletId of(String value) {
        return new WalletId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
