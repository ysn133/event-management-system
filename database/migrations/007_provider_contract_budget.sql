BEGIN;
ALTER TABLE contrats_prestataire
    ADD COLUMN IF NOT EXISTS request_id UUID,
    ADD COLUMN IF NOT EXISTS budget_sync_state VARCHAR(20) NOT NULL DEFAULT 'SYNCED',
    ADD COLUMN IF NOT EXISTS budget_sync_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pending_payload JSONB;
CREATE UNIQUE INDEX IF NOT EXISTS uk_provider_contract_request ON contrats_prestataire(request_id) WHERE request_id IS NOT NULL;
ALTER TABLE depenses
    ADD COLUMN IF NOT EXISTS contrat_prestataire_id BIGINT,
    ADD COLUMN IF NOT EXISTS contrat_prestataire_version BIGINT,
    ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_expense_provider_contract ON depenses(contrat_prestataire_id) WHERE contrat_prestataire_id IS NOT NULL;
-- The external identifier deliberately has no FK: Prestataires owns contracts.
COMMIT;
