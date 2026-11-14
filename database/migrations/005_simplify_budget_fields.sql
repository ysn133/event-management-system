BEGIN;

ALTER TABLE depenses
    DROP COLUMN IF EXISTS budget_categorie_id,
    DROP COLUMN IF EXISTS sous_categorie,
    DROP COLUMN IF EXISTS prestataire_id,
    DROP COLUMN IF EXISTS fournisseur_nom,
    DROP COLUMN IF EXISTS taux_tva,
    DROP COLUMN IF EXISTS mode_paiement,
    DROP COLUMN IF EXISTS reference_facture,
    DROP COLUMN IF EXISTS centre_cout,
    DROP COLUMN IF EXISTS created_by;

DROP TABLE IF EXISTS budget_categories;

ALTER TABLE budgets
    DROP COLUMN IF EXISTS nom,
    DROP COLUMN IF EXISTS devise,
    DROP COLUMN IF EXISTS date_debut,
    DROP COLUMN IF EXISTS date_fin,
    DROP COLUMN IF EXISTS responsable_id,
    DROP COLUMN IF EXISTS seuil_alerte,
    DROP COLUMN IF EXISTS alertes_automatiques,
    DROP COLUMN IF EXISTS justification_depassement_obligatoire,
    DROP COLUMN IF EXISTS notes;

COMMIT;
