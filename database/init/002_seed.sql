BEGIN;

INSERT INTO organisateurs (id, nom, email, mot_de_passe_hash)
VALUES (
    1,
    'EventFlow Team 20 Shared Organizer',
    'team20@eventflow.local',
    'development-only-not-a-real-password-hash'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO evenements (
    id,
    organisateur_id,
    titre,
    description,
    date_debut,
    date_fin,
    lieu,
    statut
)
VALUES (
    1,
    1,
    'EventFlow Team 20 Shared Test Event',
    'Stable shared event for Team 20 cross-service development.',
    '2026-12-15T09:00:00Z',
    '2026-12-15T18:00:00Z',
    'Casablanca',
    'PLANIFIE'
)
ON CONFLICT (id) DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('organisateurs', 'id'),
    GREATEST((SELECT MAX(id) FROM organisateurs), 1),
    true
);

SELECT setval(
    pg_get_serial_sequence('evenements', 'id'),
    GREATEST((SELECT MAX(id) FROM evenements), 1),
    true
);

COMMIT;
