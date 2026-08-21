-- Barrera de deduplicación para el crédito de Wallet (schema finance_ops).
--
-- NO es read model: participa en si WalletCreditor despacha o no
-- CreditWalletCommand, así que el ADR-0001 prohíbe resolverlo contra una
-- proyección. Mismo patrón que provider_webhook_inboxes /
-- deposit_provider_references: un UNIQUE + insert idempotente, no un
-- "existe" seguido de un insert aparte, que dejaría una carrera entre
-- entregas concurrentes.

-- Traduce qué depósitos ya acreditaron qué wallet. Sin ella, un
-- DepositSucceededEvent reentregado por Axon (at-least-once) despacharía
-- CreditWalletCommand dos veces para el mismo depósito.
CREATE TABLE finance_ops.wallet_credited_deposits (
    id uuid NOT NULL,
    wallet_id uuid NOT NULL,
    deposit_id uuid NOT NULL,
    credited_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT wallet_credited_deposits_pkey PRIMARY KEY (id),
    CONSTRAINT uq_wallet_credited_deposits_wallet_deposit UNIQUE (wallet_id, deposit_id)
);
