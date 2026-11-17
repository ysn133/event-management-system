BEGIN;
ALTER TABLE depenses
    ADD COLUMN IF NOT EXISTS source_cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS source_missing BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS source_fingerprint VARCHAR(64),
    ADD COLUMN IF NOT EXISTS source_reviewed_at TIMESTAMPTZ;
UPDATE depenses SET source_cancelled=TRUE
WHERE statut='ANNULEE' AND (reservation_salle_id IS NOT NULL OR contrat_prestataire_id IS NOT NULL);
COMMIT;
