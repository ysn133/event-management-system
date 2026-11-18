-- Runs within the import transaction, with application services stopped.
LOCK TABLE organisateurs, evenements, budgets, depenses, salles,
    reservations_salle, prestataires, services_prestataire,
    contrats_prestataire, invites, invitations IN ACCESS EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM evenements) OR EXISTS (SELECT 1 FROM budgets)
        OR EXISTS (SELECT 1 FROM depenses) OR EXISTS (SELECT 1 FROM salles)
        OR EXISTS (SELECT 1 FROM reservations_salle) OR EXISTS (SELECT 1 FROM prestataires)
        OR EXISTS (SELECT 1 FROM services_prestataire) OR EXISTS (SELECT 1 FROM contrats_prestataire)
        OR EXISTS (SELECT 1 FROM invites) OR EXISTS (SELECT 1 FROM invitations) THEN
        RAISE EXCEPTION 'Target contains business data. Import refused; nothing overwritten.';
    END IF;
END $$;
