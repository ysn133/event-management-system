BEGIN;

ALTER TABLE depenses
    ADD COLUMN IF NOT EXISTS reservation_salle_id BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS uk_depenses_reservation_salle_id
    ON depenses (reservation_salle_id)
    WHERE reservation_salle_id IS NOT NULL;

COMMIT;
