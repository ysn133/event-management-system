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

INSERT INTO budgets (id, evenement_id, montant_total, statut)
VALUES (1, 1, 50000.00, 'OUVERT')
ON CONFLICT (evenement_id) DO NOTHING;

INSERT INTO depenses (
    budget_id,
    description,
    montant,
    categorie,
    date_depense,
    statut
)
SELECT
    b.id,
    sample.description,
    sample.montant,
    sample.categorie,
    sample.date_depense,
    'PAYEE'
FROM budgets b
CROSS JOIN (
    VALUES
        ('Location de la salle principale', 12000.00::NUMERIC, 'Salle', '2026-11-01'::DATE),
        ('Service traiteur', 8500.00::NUMERIC, 'Restauration', '2026-11-03'::DATE),
        ('Sonorisation et éclairage', 4800.00::NUMERIC, 'Technique', '2026-11-05'::DATE),
        ('Supports imprimés et badges', 2200.00::NUMERIC, 'Communication', '2026-11-06'::DATE),
        ('Photographe événementiel', 3500.00::NUMERIC, 'Prestataire', '2026-11-07'::DATE)
) AS sample(description, montant, categorie, date_depense)
WHERE b.evenement_id = 1
  AND NOT EXISTS (
      SELECT 1
      FROM depenses d
      WHERE d.budget_id = b.id
        AND d.description = sample.description
  );

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

SELECT setval(
    pg_get_serial_sequence('budgets', 'id'),
    GREATEST((SELECT MAX(id) FROM budgets), 1),
    true
);

SELECT setval(
    pg_get_serial_sequence('depenses', 'id'),
    GREATEST((SELECT MAX(id) FROM depenses), 1),
    true
);

COMMIT;
