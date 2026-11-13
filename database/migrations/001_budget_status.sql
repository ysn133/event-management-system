ALTER TABLE budgets
    ADD COLUMN IF NOT EXISTS statut VARCHAR(20);

UPDATE budgets
SET statut = 'OUVERT'
WHERE statut IS NULL;

ALTER TABLE budgets
    ALTER COLUMN statut SET DEFAULT 'OUVERT',
    ALTER COLUMN statut SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_budgets_statut'
    ) THEN
        ALTER TABLE budgets
            ADD CONSTRAINT ck_budgets_statut
            CHECK (statut IN ('OUVERT', 'CLOTURE'));
    END IF;
END $$;
