BEGIN;

-- IDs 1001-1099 are reserved for repeatable local demo data.
INSERT INTO organisateurs (id, nom, email, mot_de_passe_hash)
VALUES (1001, 'EventFlow Demo Organizer', 'demo@eventflow.local', 'demo-only-password-hash')
ON CONFLICT (id) DO UPDATE SET
    nom = EXCLUDED.nom,
    email = EXCLUDED.email,
    mot_de_passe_hash = EXCLUDED.mot_de_passe_hash;

INSERT INTO evenements (
    id, organisateur_id, titre, description, date_debut, date_fin, lieu, statut
)
VALUES
    (1001, 1001, 'Forum des entrepreneurs',
     'Forum professionnel avec conférences, ateliers et espace de networking.',
     '2026-12-15T09:00:00Z', '2026-12-15T18:00:00Z', 'Casablanca', 'PLANIFIE'),
    (1002, 1001, 'Conférence Web Services',
     'Conférence technique consacrée aux architectures distribuées.',
     '2026-12-20T08:30:00Z', '2026-12-20T17:30:00Z', 'Rabat', 'CONFIRME'),
    (1003, 1001, 'Salon digital EventFlow',
     'Démonstration en cours des différents services de la plateforme.',
     '2026-11-13T08:00:00Z', '2026-11-13T20:00:00Z', 'Marrakech', 'EN_COURS'),
    (1004, 1001, 'Séminaire architecture logicielle',
     'Événement terminé conservé pour tester les historiques et rapports.',
     '2026-10-10T09:00:00Z', '2026-10-10T16:00:00Z', 'Agadir', 'TERMINE'),
    (1005, 1001, 'Atelier communication',
     'Événement annulé utilisé pour tester les états terminaux.',
     '2026-12-28T10:00:00Z', '2026-12-28T15:00:00Z', 'Tanger', 'ANNULE')
ON CONFLICT (id) DO UPDATE SET
    organisateur_id = EXCLUDED.organisateur_id,
    titre = EXCLUDED.titre,
    description = EXCLUDED.description,
    date_debut = EXCLUDED.date_debut,
    date_fin = EXCLUDED.date_fin,
    lieu = EXCLUDED.lieu,
    statut = EXCLUDED.statut;

INSERT INTO prestataires (id, nom, contact, type_service, tarif)
VALUES
    (1001, 'Saveurs du Maroc', 'contact@saveurs-demo.local', 'TRAITEUR', 8500.00),
    (1002, 'Atlas Events Tech', 'tech@atlas-demo.local', 'TECHNIQUE', 4800.00),
    (1003, 'Studio Jasmin', 'studio@jasmin-demo.local', 'DECORATION', 3500.00),
    (1004, 'SecurePro', 'contact@securepro-demo.local', 'SECURITE', 2200.00)
ON CONFLICT (id) DO UPDATE SET
    nom = EXCLUDED.nom,
    contact = EXCLUDED.contact,
    type_service = EXCLUDED.type_service,
    tarif = EXCLUDED.tarif;

INSERT INTO services_prestataire (id, prestataire_id, nom, description, tarif)
VALUES
    (1001, 1001, 'Buffet complet', 'Déjeuner, pauses café et boissons.', 6500.00),
    (1002, 1001, 'Cocktail de clôture', 'Cocktail pour 150 personnes.', 4200.00),
    (1003, 1002, 'Sonorisation', 'Son, micros et assistance technique.', 3000.00),
    (1004, 1002, 'Éclairage scénique', 'Éclairage de la scène principale.', 1800.00),
    (1005, 1003, 'Décoration complète', 'Accueil, scène et signalétique.', 3500.00),
    (1006, 1004, 'Équipe de sécurité', 'Quatre agents pendant une journée.', 2200.00)
ON CONFLICT (id) DO UPDATE SET
    prestataire_id = EXCLUDED.prestataire_id,
    nom = EXCLUDED.nom,
    description = EXCLUDED.description,
    tarif = EXCLUDED.tarif;

INSERT INTO contrats_prestataire (
    id, prestataire_id, evenement_id, prestation, montant, date_prestation, statut
)
VALUES
    (1001, 1001, 1001, 'Restauration complète', 8500.00, '2026-12-15T10:00:00Z', 'CONFIRME'),
    (1002, 1002, 1001, 'Sonorisation et éclairage', 4800.00, '2026-12-15T08:00:00Z', 'CONFIRME'),
    (1003, 1003, 1002, 'Décoration de la conférence', 3500.00, '2026-12-20T07:30:00Z', 'EN_ATTENTE'),
    (1004, 1004, 1003, 'Sécurité du salon', 2200.00, '2026-11-13T07:30:00Z', 'TERMINE')
ON CONFLICT (id) DO UPDATE SET
    prestataire_id = EXCLUDED.prestataire_id,
    evenement_id = EXCLUDED.evenement_id,
    prestation = EXCLUDED.prestation,
    montant = EXCLUDED.montant,
    date_prestation = EXCLUDED.date_prestation,
    statut = EXCLUDED.statut;

INSERT INTO salles (id, nom, capacite, adresse, prix)
VALUES
    (1001, 'Palais des Congrès', 1200, 'Avenue des Congrès, Casablanca', 18000.00),
    (1002, 'Atlas Ballroom', 450, 'Boulevard Atlas, Rabat', 12500.00),
    (1003, 'Riad Al Bahja', 280, 'Médina, Marrakech', 9800.00),
    (1004, 'Espace Bouregreg', 650, 'Marina Bouregreg, Salé', 14000.00)
ON CONFLICT (id) DO UPDATE SET
    nom = EXCLUDED.nom,
    capacite = EXCLUDED.capacite,
    adresse = EXCLUDED.adresse,
    prix = EXCLUDED.prix;

INSERT INTO reservations_salle (
    id, salle_id, evenement_id, date_debut, date_fin, prix, statut
)
VALUES
    (1001, 1001, 1001, '2026-12-15T08:00:00Z', '2026-12-15T19:00:00Z', 18000.00, 'CONFIRMEE'),
    (1002, 1002, 1002, '2026-12-20T07:30:00Z', '2026-12-20T18:30:00Z', 12500.00, 'CONFIRMEE'),
    (1003, 1003, 1003, '2026-11-13T07:00:00Z', '2026-11-13T21:00:00Z', 9800.00, 'CONFIRMEE'),
    (1004, 1004, 1005, '2026-12-28T09:00:00Z', '2026-12-28T16:00:00Z', 14000.00, 'ANNULEE')
ON CONFLICT (id) DO UPDATE SET
    salle_id = EXCLUDED.salle_id,
    evenement_id = EXCLUDED.evenement_id,
    date_debut = EXCLUDED.date_debut,
    date_fin = EXCLUDED.date_fin,
    prix = EXCLUDED.prix,
    statut = EXCLUDED.statut;

INSERT INTO budgets (id, evenement_id, montant_total, statut)
VALUES
    (1001, 1001, 50000.00, 'OUVERT'),
    (1002, 1002, 35000.00, 'OUVERT'),
    (1003, 1003, 42000.00, 'OUVERT'),
    (1004, 1004, 28000.00, 'CLOTURE')
ON CONFLICT (id) DO UPDATE SET
    evenement_id = EXCLUDED.evenement_id,
    montant_total = EXCLUDED.montant_total,
    statut = EXCLUDED.statut;

INSERT INTO depenses (
    id, budget_id, description, montant, categorie, date_depense, statut
)
VALUES
    (1001, 1001, 'Location du Palais des Congrès', 18000.00, 'Salle', '2026-11-01', 'PAYEE'),
    (1002, 1001, 'Service traiteur', 8500.00, 'Restauration', '2026-11-03', 'EN_ATTENTE'),
    (1003, 1001, 'Sonorisation et éclairage', 4800.00, 'Technique', '2026-11-05', 'PREVUE'),
    (1004, 1001, 'Supports imprimés et badges', 2200.00, 'Communication', '2026-11-06', 'PAYEE'),
    (1005, 1002, 'Location Atlas Ballroom', 12500.00, 'Salle', '2026-11-08', 'PAYEE'),
    (1006, 1002, 'Décoration de la conférence', 3500.00, 'Décoration', '2026-11-09', 'EN_ATTENTE'),
    (1007, 1002, 'Campagne digitale', 6000.00, 'Communication', '2026-11-10', 'PREVUE'),
    (1008, 1003, 'Location Riad Al Bahja', 9800.00, 'Salle', '2026-10-28', 'PAYEE'),
    (1009, 1003, 'Photographe', 3500.00, 'Prestataire', '2026-11-02', 'ANNULEE'),
    (1010, 1004, 'Salle du séminaire', 9000.00, 'Salle', '2026-09-15', 'PAYEE'),
    (1011, 1004, 'Déjeuner du séminaire', 7200.00, 'Restauration', '2026-09-20', 'PAYEE')
ON CONFLICT (id) DO UPDATE SET
    budget_id = EXCLUDED.budget_id,
    description = EXCLUDED.description,
    montant = EXCLUDED.montant,
    categorie = EXCLUDED.categorie,
    date_depense = EXCLUDED.date_depense,
    statut = EXCLUDED.statut;

INSERT INTO invites (id, nom, email, telephone)
VALUES
    (1001, 'Meryem Alaoui', 'meryem.alaoui@demo.local', '+212600000101'),
    (1002, 'Omar Benjelloun', 'omar.benjelloun@demo.local', '+212600000102'),
    (1003, 'Sara El Fassi', 'sara.elfassi@demo.local', NULL),
    (1004, 'Amine Tazi', NULL, '+212600000104'),
    (1005, 'Nadia Idrissi', 'nadia.idrissi@demo.local', '+212600000105'),
    (1006, 'Youssef Amrani', 'youssef.amrani@demo.local', '+212600000106')
ON CONFLICT (id) DO UPDATE SET
    nom = EXCLUDED.nom,
    email = EXCLUDED.email,
    telephone = EXCLUDED.telephone;

INSERT INTO invitations (id, invite_id, evenement_id, date_envoi, statut)
VALUES
    (1001, 1001, 1001, '2026-11-10T10:00:00Z', 'ACCEPTEE'),
    (1002, 1002, 1001, '2026-11-10T10:05:00Z', 'EN_ATTENTE'),
    (1003, 1003, 1001, '2026-11-10T10:10:00Z', 'REFUSEE'),
    (1004, 1004, 1002, '2026-11-11T09:00:00Z', 'ACCEPTEE'),
    (1005, 1005, 1002, '2026-11-11T09:05:00Z', 'EN_ATTENTE'),
    (1006, 1006, 1003, NULL, 'EN_ATTENTE')
ON CONFLICT (id) DO UPDATE SET
    invite_id = EXCLUDED.invite_id,
    evenement_id = EXCLUDED.evenement_id,
    date_envoi = EXCLUDED.date_envoi,
    statut = EXCLUDED.statut;

-- Keep generated IDs above the reserved demo range. The next generated ID is
-- therefore at least 1100 and cannot later be removed by the demo reset.
SELECT setval(pg_get_serial_sequence('organisateurs', 'id'), GREATEST((SELECT MAX(id) FROM organisateurs), 1099), true);
SELECT setval(pg_get_serial_sequence('evenements', 'id'), GREATEST((SELECT MAX(id) FROM evenements), 1099), true);
SELECT setval(pg_get_serial_sequence('prestataires', 'id'), GREATEST((SELECT MAX(id) FROM prestataires), 1099), true);
SELECT setval(pg_get_serial_sequence('services_prestataire', 'id'), GREATEST((SELECT MAX(id) FROM services_prestataire), 1099), true);
SELECT setval(pg_get_serial_sequence('contrats_prestataire', 'id'), GREATEST((SELECT MAX(id) FROM contrats_prestataire), 1099), true);
SELECT setval(pg_get_serial_sequence('salles', 'id'), GREATEST((SELECT MAX(id) FROM salles), 1099), true);
SELECT setval(pg_get_serial_sequence('reservations_salle', 'id'), GREATEST((SELECT MAX(id) FROM reservations_salle), 1099), true);
SELECT setval(pg_get_serial_sequence('budgets', 'id'), GREATEST((SELECT MAX(id) FROM budgets), 1099), true);
SELECT setval(pg_get_serial_sequence('depenses', 'id'), GREATEST((SELECT MAX(id) FROM depenses), 1099), true);
SELECT setval(pg_get_serial_sequence('invites', 'id'), GREATEST((SELECT MAX(id) FROM invites), 1099), true);
SELECT setval(pg_get_serial_sequence('invitations', 'id'), GREATEST((SELECT MAX(id) FROM invitations), 1099), true);

COMMIT;
