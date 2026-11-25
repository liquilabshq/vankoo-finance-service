-- El adelanto a la MYPE cuando su subasta se fondea (schema finance_ops y
-- finance_read_model).
--
-- 1. Barrera de idempotencia del abono, mismo patrón que
--    wallet_credited_deposits (V4): un UNIQUE + INSERT ... ON CONFLICT DO
--    NOTHING antes de despachar CreditWalletFromAuctionCommand. La clave es
--    auction_id a secas, no el eventId del mensaje: una subasta se fondea una
--    sola vez, así que aunque Investment volviera a publicar el hecho con
--    otro eventId (reintento del outbox, replay del topic) se abona una vez.
CREATE TABLE finance_ops.wallet_credited_auctions (
    id uuid NOT NULL,
    wallet_id uuid NOT NULL,
    auction_id uuid NOT NULL,
    credited_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT wallet_credited_auctions_pkey PRIMARY KEY (id),
    CONSTRAINT uq_wallet_credited_auctions_auction UNIQUE (auction_id)
);

-- 2. El historial gana el tipo ADELANTO y la referencia a la subasta.
--    Nullable: solo los ADELANTO la llevan. Sigue siendo reconstruible desde
--    el historial de eventos.
ALTER TABLE finance_read_model.wallet_movements
    ADD COLUMN source_auction_id uuid;

ALTER TABLE finance_read_model.wallet_movements
    DROP CONSTRAINT ck_wallet_movements_type;

ALTER TABLE finance_read_model.wallet_movements
    ADD CONSTRAINT ck_wallet_movements_type
        CHECK (type IN ('RECARGA', 'ADELANTO', 'INVERSION', 'RETIRO', 'COMISION'));

ALTER TABLE finance_read_model.wallet_movements
    ADD CONSTRAINT ck_wallet_movements_source_auction_id_only_for_adelanto
        CHECK (
            (type = 'ADELANTO' AND source_auction_id IS NOT NULL)
            OR (type <> 'ADELANTO' AND source_auction_id IS NULL)
        );
