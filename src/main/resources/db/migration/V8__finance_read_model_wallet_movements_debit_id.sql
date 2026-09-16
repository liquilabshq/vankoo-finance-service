-- wallet_movements gana la referencia del débito (schema finance_read_model).
--
-- WalletDebitedEvent ahora lleva debitId (campo nuevo, opcional, al final):
-- es el id con el que se respondió al POST .../debits y el que el móvil pasa
-- a Investment como transactionId. Se proyecta aquí para que el historial
-- permita cruzar un movimiento INVERSION con la participación que generó.
--
-- Nullable a propósito: los créditos no tienen débito, y los
-- WalletDebitedEvent persistidos antes de que existiera el campo cargan con
-- null. No hay backfill porque no existía ningún emisor de débitos antes de
-- esta tarjeta. Sigue siendo reconstruible desde el historial de eventos.

ALTER TABLE finance_read_model.wallet_movements
    ADD COLUMN debit_id uuid;

-- Un crédito nunca lleva debit_id; un débito puede o no llevarlo (eventos
-- viejos).
ALTER TABLE finance_read_model.wallet_movements
    ADD CONSTRAINT ck_wallet_movements_debit_id_only_for_debits
        CHECK (direction = 'DEBIT' OR debit_id IS NULL);
