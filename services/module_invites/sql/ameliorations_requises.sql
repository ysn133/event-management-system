-- =============================================================================
-- Améliorations REQUISES du schéma (module Invités)
-- =============================================================================
-- Ce script ne recrée AUCUNE table et ne touche pas aux tables des autres modules.
-- Il est idempotent.
--
-- >>> Base commune EventFlow (database/init/001_schema.sql du dépôt de l'équipe) :
-- >>> AUCUNE modification n'est nécessaire. L'unicité (uk_invitations_invite_evenement) et
-- >>> l'index (idx_invitations_evenement_id) existent déjà ; ce script le constate et ne fait rien.
--
-- Il ne sert que pour une base issue du schéma du cahier des charges initial, qui n'a pas :
--
-- 1. UNIQUE (invite_id, evenement_id) sur public.invitations
--    Pourquoi : sans elle, deux requêtes simultanées peuvent associer deux fois le même invité
--    au même événement (le contrôle applicatif seul ne suffit pas). L'application convertit la
--    violation de cette contrainte en réponse 409.
--
-- 2. INDEX sur public.invitations (evenement_id)
--    Pourquoi : lister les invitations d'un événement et calculer ses statistiques filtrent sur
--    evenement_id ; PostgreSQL n'indexe pas automatiquement les clés étrangères.
--
-- L'unicité et l'index sont recherchés PAR COLONNES (quel que soit leur nom) : une contrainte
-- équivalente n'est jamais dupliquée.
--
-- Prérequis : aucun doublon (invite_id, evenement_id). Sinon le script s'arrête avec un message
-- explicite SANS rien modifier.
--
-- Exécution :  psql "$DATABASE_URL" -f sql/ameliorations_requises.sql
-- =============================================================================

BEGIN;

DO $$
DECLARE
    nombre_doublons integer;
    unicite_presente boolean;
    index_present boolean;
BEGIN
    SELECT EXISTS (
        SELECT 1
        FROM pg_index i
        WHERE i.indrelid = 'public.invitations'::regclass
          AND i.indisunique
          AND i.indpred IS NULL
          AND (
              SELECT array_agg(a.attname::text ORDER BY a.attname)
              FROM pg_attribute a
              WHERE a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
          ) = ARRAY['evenement_id', 'invite_id']
    ) INTO unicite_presente;

    IF unicite_presente THEN
        RAISE NOTICE 'Unicité (invite_id, evenement_id) déjà présente : rien à faire.';
    ELSE
        SELECT count(*) INTO nombre_doublons
        FROM (
            SELECT invite_id, evenement_id
            FROM public.invitations
            GROUP BY invite_id, evenement_id
            HAVING count(*) > 1
        ) AS doublons;

        IF nombre_doublons > 0 THEN
            RAISE EXCEPTION
                'Contrainte non créée : % couple(s) (invite_id, evenement_id) en double. '
                'Détectez-les avec : SELECT invite_id, evenement_id, count(*) FROM public.invitations '
                'GROUP BY 1, 2 HAVING count(*) > 1;', nombre_doublons;
        END IF;

        ALTER TABLE public.invitations
            ADD CONSTRAINT uk_invitations_invite_evenement UNIQUE (invite_id, evenement_id);
    END IF;

    -- Tout index dont la PREMIÈRE colonne est evenement_id convient.
    SELECT EXISTS (
        SELECT 1
        FROM pg_index i
        WHERE i.indrelid = 'public.invitations'::regclass
          AND i.indkey[0] = (
              SELECT a.attnum FROM pg_attribute a
              WHERE a.attrelid = i.indrelid AND a.attname = 'evenement_id'
          )
    ) INTO index_present;

    IF index_present THEN
        RAISE NOTICE 'Index sur evenement_id déjà présent : rien à faire.';
    ELSE
        CREATE INDEX idx_invitations_evenement_id ON public.invitations (evenement_id);
    END IF;
END
$$;

COMMIT;
