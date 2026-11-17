BEGIN;
ALTER TABLE reservations_salle
    ADD COLUMN IF NOT EXISTS request_id UUID,
    ADD COLUMN IF NOT EXISTS budget_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS budget_sync_state VARCHAR(20) NOT NULL DEFAULT 'SYNCED',
    ADD COLUMN IF NOT EXISTS budget_approved BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS pending_payload TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS uk_room_reservation_request ON reservations_salle(request_id) WHERE request_id IS NOT NULL;
ALTER TABLE depenses ADD COLUMN IF NOT EXISTS reservation_salle_version BIGINT;
COMMIT;
