-- Barrera de idempotencia para
-- POST /api/v1/accounts/{accountId}/wallets/{currency}/debits (schema finance_ops).
--
-- Gemela de deposit_command_idempotencies (V5), con la misma justificación:
-- participa en si WalletCommandServiceImpl despacha o no DebitWalletCommand,
-- así que el ADR-0001 prohíbe resolverlo contra una proyección. Aquí la
-- carrera que cierra el UNIQUE es literalmente el bug de la tarjeta #55: dos
-- solicitudes con la misma Idempotency-Key (un reintento del móvil) no pueden
-- debitar dos veces.
--
-- Nombre en plural por la naming strategy (ver V5).

CREATE TABLE finance_ops.wallet_debit_command_idempotencies (
    id uuid NOT NULL,
    account_id uuid NOT NULL,
    idempotency_key character varying(255) NOT NULL,
    -- El debitId acuñado la primera vez que se vio esta
    -- (account_id, idempotency_key). Un reintento con el mismo contenido
    -- devuelve este valor sin despachar un comando nuevo; es el mismo id que
    -- viaja en WalletDebitedEvent y que el móvil entrega a Investment como
    -- transactionId.
    debit_id uuid NOT NULL,
    -- SHA-256 hex de los campos significativos de la solicitud (accountId,
    -- currency, amountMinor, reason). Distingue un reintento idéntico de un
    -- reintento con contenido distinto bajo la misma clave.
    request_hash character varying(64) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT wallet_debit_command_idempotencies_pkey PRIMARY KEY (id),
    -- BARRERA: la misma (account_id, idempotency_key) nunca despacha
    -- DebitWalletCommand dos veces.
    CONSTRAINT uq_wallet_debit_command_idempotencies_account_key UNIQUE (account_id, idempotency_key)
);
