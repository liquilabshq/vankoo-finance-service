-- Tablas operativas del borde (schema finance_ops).
--
-- NO son read model: participan en decisiones de admisión, y el ADR-0001 prohíbe
-- validar admisiones contra una proyección. Son reconstruibles desde el historial
-- de eventos y el log de webhooks; perderlas degrada la deduplicación, no corrompe
-- el historial.
--
-- Los nombres van en plural igual que en V1, porque la naming strategy del proyecto
-- (SnakeCaseWithPluralizedTablePhysicalNamingStrategy) pluraliza el nombre derivado
-- de la @Entity y ddl-auto=validate compara contra eso, no contra el nombre singular
-- que usa el contrato en prosa.

CREATE SCHEMA IF NOT EXISTS finance_ops;

-- Inbox de webhooks. Un solo componente resuelve deduplicación, orden de llegada
-- y reintentos; esta tabla es su estado.
CREATE TABLE finance_ops.provider_webhook_inboxes (
    id uuid NOT NULL,
    provider character varying(255) NOT NULL,
    provider_event_id character varying(255) NOT NULL,
    provider_deposit_id character varying(255) NOT NULL,
    -- Resuelto contra deposit_provider_references. NULL mientras la referencia
    -- del proveedor todavía no se ha registrado: ese es el caso PARKED.
    deposit_id uuid,
    status character varying(255) NOT NULL,             -- RECEIVED | APPLIED | PARKED | DISCARDED
    normalized_status character varying(255) NOT NULL,
    failure_reason character varying(255),
    cancellation_reason character varying(255),
    observed_at timestamp(6) with time zone NOT NULL,
    -- SHA-256 hex del payload crudo. El cuerpo NO se retiene: los campos
    -- normalizados de arriba son todo lo que el reintento necesita, así que no
    -- guardamos datos de Stripe sin una política de purga definida.
    payload_ref character varying(255) NOT NULL,
    attempts integer NOT NULL,
    next_attempt_at timestamp(6) with time zone,
    last_error character varying(1023),
    received_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    CONSTRAINT provider_webhook_inboxes_pkey PRIMARY KEY (id),
    -- PRIMERA BARRERA contra el doble conteo: un evento repetido del proveedor
    -- no llega a crear fila, así que no se emite un segundo comando.
    CONSTRAINT uq_provider_webhook_inboxes_event UNIQUE (provider, provider_event_id),
    -- Los mismos CHECK que Hibernate generaría para estos enums. ddl-auto=validate
    -- no los comprueba, pero se escriben porque las filas se insertan con SQL nativo
    -- y estas columnas son la única defensa contra un valor fuera de la taxonomía.
    CONSTRAINT ck_provider_webhook_inboxes_provider
        CHECK (provider IN ('STRIPE')),
    CONSTRAINT ck_provider_webhook_inboxes_status
        CHECK (status IN ('RECEIVED', 'APPLIED', 'PARKED', 'DISCARDED')),
    CONSTRAINT ck_provider_webhook_inboxes_normalized_status
        CHECK (normalized_status IN ('ACTION_REQUIRED', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_provider_webhook_inboxes_failure_reason
        CHECK (failure_reason IN ('DECLINED', 'EXPIRED', 'INVALID_PAYMENT_METHOD', 'PROVIDER_ERROR', 'UNKNOWN'))
);

-- Soporta el barrido: filas pendientes cuyo next_attempt_at ya venció.
CREATE INDEX idx_provider_webhook_inboxes_due
    ON finance_ops.provider_webhook_inboxes (status, next_attempt_at);

-- Traduce la referencia del proveedor a nuestro depositId. Sin ella el inbox no
-- puede resolver a qué recarga pertenece un webhook, y lo aparca.
CREATE TABLE finance_ops.deposit_provider_references (
    id uuid NOT NULL,
    provider character varying(255) NOT NULL,
    provider_deposit_id character varying(255) NOT NULL,
    deposit_id uuid NOT NULL,
    registered_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT deposit_provider_references_pkey PRIMARY KEY (id),
    CONSTRAINT uq_deposit_provider_references_reference UNIQUE (provider, provider_deposit_id),
    CONSTRAINT ck_deposit_provider_references_provider CHECK (provider IN ('STRIPE'))
);
