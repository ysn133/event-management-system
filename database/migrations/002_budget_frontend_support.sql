BEGIN;

ALTER TABLE budgets
    ADD COLUMN IF NOT EXISTS depassement_autorise BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE depenses
    ADD COLUMN IF NOT EXISTS commentaire TEXT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_depenses_budget_statut ON depenses (budget_id, statut);
CREATE INDEX IF NOT EXISTS idx_depenses_budget_date ON depenses (budget_id, date_depense);

DROP TRIGGER IF EXISTS trg_budgets_updated_at ON budgets;
CREATE TRIGGER trg_budgets_updated_at
BEFORE UPDATE ON budgets
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_depenses_updated_at ON depenses;
CREATE TRIGGER trg_depenses_updated_at
BEFORE UPDATE ON depenses
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMIT;
