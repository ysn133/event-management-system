BEGIN;

-- These optional features were removed to keep the Budget service focused on
-- budgets and expenses. Alerts are calculated on demand
-- from the budget threshold and current expense totals.
DROP TABLE IF EXISTS depense_justificatifs;
DROP TABLE IF EXISTS depense_tags;
DROP TABLE IF EXISTS depense_audit_logs;
DROP TABLE IF EXISTS budget_alertes;

COMMIT;
