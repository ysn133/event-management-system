-- =============================================================================
-- Améliorations OPTIONNELLES (NON utilisées par l'application dans cette version)
-- =============================================================================
-- Ajoute un état technique d'envoi STOCKÉ, distinct de la réponse de l'invité (statut) :
--   envoi_statut : NON_ENVOYEE | ENVOYEE | ECHEC
--   envoi_erreur : message de la dernière erreur d'envoi (NULL sinon)
--
-- Dans la version 1, l'application calcule l'état d'envoi à partir de date_envoi
-- (NULL = non envoyée) et ne lit ni n'écrit ces colonnes. Elles serviraient à conserver les
-- échecs d'envoi en base et préparer un mécanisme Outbox (voir « Limites connues » du README).
-- N'exécuter ce script que si l'équipe décide de faire évoluer le code en conséquence.
--
-- Idempotent. Exécution :  psql "$DATABASE_URL" -f sql/ameliorations_optionnelles.sql
-- =============================================================================

BEGIN;

ALTER TABLE public.invitations
    ADD COLUMN IF NOT EXISTS envoi_statut character varying NOT NULL DEFAULT 'NON_ENVOYEE',
    ADD COLUMN IF NOT EXISTS envoi_erreur text;

-- Cohérence avec les invitations déjà envoyées (date_envoi renseignée).
UPDATE public.invitations
SET envoi_statut = 'ENVOYEE'
WHERE date_envoi IS NOT NULL AND envoi_statut = 'NON_ENVOYEE';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'invitations_envoi_statut_check'
          AND conrelid = 'public.invitations'::regclass
    ) THEN
        ALTER TABLE public.invitations
            ADD CONSTRAINT invitations_envoi_statut_check
            CHECK (envoi_statut IN ('NON_ENVOYEE', 'ENVOYEE', 'ECHEC'));
    END IF;
END
$$;

COMMIT;
