-- Barrera de idempotencia para POST /v1/deposits (schema finance_ops).
--
-- NO es read model: participa en si DepositCommandServiceImpl despacha o no
-- InitiateDepositCommand, así que el ADR-0001 prohíbe resolverlo contra una
-- proyección. Mismo patrón que provider_webhook_inboxes / wallet_credited_deposits:
-- un UNIQUE + insert idempotente como barrera, no un "existe" seguido de un
-- insert aparte, que dejaría una carrera entre dos solicitudes concurrentes
-- con la misma Idempotency-Key.
--
-- El nombre de tabla es plural (deposit_command_idempotencies) y no
-- "deposit_command_idempotency" como en la prosa del contrato, por la misma
-- razón que provider_webhook_inboxes/deposit_views: la naming strategy
-- pluraliza el nombre derivado de la @Entity y ddl-auto=validate compara
-- contra eso, no contra el nombre singular que usa el contrato en prosa.

CREATE TABLE finance_ops.deposit_command_idempotencies (
    id uuid NOT NULL,
    account_id uuid NOT NULL,
    idempotency_key character varying(255) NOT NULL,
    -- El depositId que InitiateDepositCommand recibió la primera vez que se
    -- vio esta (account_id, idempotency_key). Un reintento con el mismo
    -- contenido devuelve este valor sin despachar un comando nuevo.
    deposit_id uuid NOT NULL,
    -- SHA-256 hex de los campos significativos de la solicitud (accountId,
    -- amountMinor, currency, provider, description). Distingue un reintento
    -- idéntico de un reintento con contenido distinto bajo la misma clave.
    request_hash character varying(64) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT deposit_command_idempotencies_pkey PRIMARY KEY (id),
    -- BARRERA: la misma (account_id, idempotency_key) nunca despacha
    -- InitiateDepositCommand dos veces.
    CONSTRAINT uq_deposit_command_idempotencies_account_key UNIQUE (account_id, idempotency_key)
);
