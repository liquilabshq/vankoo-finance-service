-- Read Model de Wallet (schema finance_read_model, ya creado en V3).
--
-- Dos tablas, dos formas distintas de proyección para los mismos 3 eventos:
--   - wallet_views: upsert en el lugar, una fila por wallet — misma forma que
--     deposit_views, con el mismo guard last_event_id/alreadyApplied.
--   - wallet_movements: append-only, una fila nueva por evento, nunca se
--     actualiza — la primera proyección append-only de este servicio. No hay
--     fila previa contra la que comparar "¿ya se aplicó este evento?", así
--     que la barrera de deduplicación es la misma que ya usan las tablas de
--     finance_ops (insertIfAbsent vía UNIQUE + ON CONFLICT DO NOTHING), no el
--     patrón alreadyApplied de una fila cargada.
--
-- Ambas son reconstruibles por completo desde el historial de eventos:
-- reseteando el token del processing group "wallet-read-model" se recrean
-- sin pérdida de información de negocio.
--
-- Los nombres van en plural (wallet_views, wallet_movements), igual que
-- deposit_views: la naming strategy del proyecto
-- (SnakeCaseWithPluralizedTablePhysicalNamingStrategy) pluraliza el nombre
-- derivado de la @Entity y ddl-auto=validate compara contra eso.

CREATE TABLE finance_read_model.wallet_views (
    -- Clave técnica sintética: Hibernate no permite un AttributeConverter
    -- directamente sobre @Id, así que wallet_id no puede doblar como PK.
    id uuid NOT NULL,
    wallet_id uuid NOT NULL,
    account_id uuid NOT NULL,
    currency character varying(255) NOT NULL,
    balance_minor bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    -- Id del EventMessage de Axon del último evento aplicado, para que la
    -- proyección sea idempotente ante redelivery/replay.
    last_event_id character varying(255) NOT NULL,
    projection_version integer NOT NULL,
    CONSTRAINT wallet_views_pkey PRIMARY KEY (id),
    CONSTRAINT uq_wallet_views_wallet_id UNIQUE (wallet_id),
    CONSTRAINT ck_wallet_views_currency
        CHECK (currency IN ('PEN', 'USD'))
);

-- Soporta GetWalletBalanceQuery: (accountId, currency) es la clave de negocio
-- que el cliente siempre entrega; wallet_id ya tiene su propio índice único.
CREATE INDEX idx_wallet_views_account_currency
    ON finance_read_model.wallet_views (account_id, currency);

-- Historial de movimientos, append-only: nunca se actualiza una fila una vez
-- insertada. event_id es la clave de deduplicación en insert, no wallet_id
-- (varios movimientos comparten wallet_id) ni id (sintético, UUIDv7, solo
-- para localidad de inserción en el índice).
CREATE TABLE finance_read_model.wallet_movements (
    id uuid NOT NULL,
    event_id character varying(255) NOT NULL,
    wallet_id uuid NOT NULL,
    account_id uuid NOT NULL,
    currency character varying(255) NOT NULL,
    amount_minor bigint NOT NULL,
    -- RECARGA es un rótulo del Read Model, no un valor de WalletMovementType:
    -- WalletCreditedEvent no trae un tipo propio.
    type character varying(255) NOT NULL,
    direction character varying(255) NOT NULL,
    -- NULL salvo cuando type = 'RECARGA'.
    source_deposit_id uuid,
    occurred_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT wallet_movements_pkey PRIMARY KEY (id),
    CONSTRAINT uq_wallet_movements_event_id UNIQUE (event_id),
    CONSTRAINT ck_wallet_movements_currency
        CHECK (currency IN ('PEN', 'USD')),
    CONSTRAINT ck_wallet_movements_type
        CHECK (type IN ('RECARGA', 'INVERSION', 'RETIRO', 'COMISION')),
    CONSTRAINT ck_wallet_movements_direction
        CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT ck_wallet_movements_source_deposit_id_only_for_recarga
        CHECK (
            (type = 'RECARGA' AND source_deposit_id IS NOT NULL)
            OR (type <> 'RECARGA' AND source_deposit_id IS NULL)
        )
);

-- Soporta ListWalletMovementsQuery: paginado y ordenado por recencia dentro
-- de un wallet.
CREATE INDEX idx_wallet_movements_wallet_occurred
    ON finance_read_model.wallet_movements (wallet_id, occurred_at DESC);
