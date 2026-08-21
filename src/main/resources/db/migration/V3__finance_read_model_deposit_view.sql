-- Read Model de Deposit (schema finance_read_model).
--
-- Proyección reconstruible por completo desde el historial de eventos: si se
-- pierde esta tabla, se reconstruye reseteando el token del processing group
-- "deposit-read-model", sin pérdida de información de negocio.
--
-- El nombre va en plural (deposit_views), igual que en V1 y V2, porque la
-- naming strategy del proyecto (SnakeCaseWithPluralizedTablePhysicalNamingStrategy)
-- pluraliza el nombre derivado de la @Entity y ddl-auto=validate compara contra
-- eso, no contra el nombre singular que usa el contrato en prosa.

CREATE SCHEMA IF NOT EXISTS finance_read_model;

CREATE TABLE finance_read_model.deposit_views (
    -- Clave técnica sintética: Hibernate no permite un AttributeConverter
    -- directamente sobre @Id, así que deposit_id no puede doblar como PK (mismo
    -- patrón que las tablas de finance_ops).
    id uuid NOT NULL,
    deposit_id uuid NOT NULL,
    account_id uuid NOT NULL,
    amount_minor bigint NOT NULL,
    currency character varying(255) NOT NULL,
    provider character varying(255) NOT NULL,
    -- NULL hasta que se registra la referencia del proveedor.
    provider_deposit_id character varying(255),
    description character varying(500),
    status character varying(255) NOT NULL,
    -- URL de acción de Stripe Checkout, si aplica y sigue vigente.
    action_url character varying(2048),
    failure_reason character varying(255),
    -- Texto libre: a diferencia de failure_reason, el proveedor no lo entrega
    -- contra una taxonomía cerrada, así que no lleva CHECK.
    cancellation_reason character varying(255),
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    -- Id del EventMessage de Axon del último evento aplicado, para que la
    -- proyección sea idempotente ante redelivery/replay.
    last_event_id character varying(255) NOT NULL,
    projection_version integer NOT NULL,
    CONSTRAINT deposit_views_pkey PRIMARY KEY (id),
    CONSTRAINT uq_deposit_views_deposit_id UNIQUE (deposit_id),
    CONSTRAINT ck_deposit_views_currency
        CHECK (currency IN ('PEN', 'USD')),
    CONSTRAINT ck_deposit_views_provider
        CHECK (provider IN ('STRIPE')),
    CONSTRAINT ck_deposit_views_status
        CHECK (status IN ('PENDING', 'ACTION_REQUIRED', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_deposit_views_failure_reason
        CHECK (failure_reason IN ('DECLINED', 'EXPIRED', 'INVALID_PAYMENT_METHOD', 'PROVIDER_ERROR', 'UNKNOWN'))
);

-- Soporta ListDepositsByAccount: paginado y ordenado por recencia.
CREATE INDEX idx_deposit_views_account_created
    ON finance_read_model.deposit_views (account_id, created_at DESC);
