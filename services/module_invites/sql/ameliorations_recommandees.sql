-- =============================================================================
-- Amélioration RECOMMANDÉE (non requise) : contrôle des statuts en base
-- =============================================================================
-- La base commune EventFlow n'a pas de contrainte CHECK sur invitations.statut (le cahier des
-- charges initial en avait une). L'application n'accepte déjà que EN_ATTENTE, ACCEPTEE et REFUSEE,
-- mais seule une contrainte en base protège contre une écriture directe (autre module, script).
--
-- Idempotent. S'arrête avec un message explicite, SANS rien modifier, si des lignes existantes ont
-- un statut hors liste. À faire valider par l'équipe : le schéma est partagé.
--
-- Exécution :  psql "$DATABASE_URL" -f sql/ameliorations_recommandees.sql
-- =============================================================================

BEGIN;

DO $$
DECLARE
    statuts_invalides text;
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.invitations'::regclass
          AND contype = 'c'
          AND conname IN ('ck_invitations_statut', 'invitations_statut_check')
    ) THEN
        RAISE NOTICE 'Contrainte sur invitations.statut déjà présente : rien à faire.';
    ELSE
        SELECT string_agg(DISTINCT statut, ', ') INTO statuts_invalides
        FROM public.invitations
        WHERE statut NOT IN ('EN_ATTENTE', 'ACCEPTEE', 'REFUSEE');

        IF statuts_invalides IS NOT NULL THEN
            RAISE EXCEPTION 'Contrainte non créée : statuts invalides présents en base : %', statuts_invalides;
        END IF;

        ALTER TABLE public.invitations
            ADD CONSTRAINT ck_invitations_statut
            CHECK (statut IN ('EN_ATTENTE', 'ACCEPTEE', 'REFUSEE'));
    END IF;
END
$$;

COMMIT;
