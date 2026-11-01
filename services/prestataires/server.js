require('dotenv').config();
const express = require('express');
const pool = require('./db');

const app = express();
app.use(express.json());

/* ---------- Helpers ---------- */

// Les colonnes numeric de PostgreSQL arrivent en texte : on les convertit.
const toPrestataire = (r) => ({
  id: Number(r.id),
  nom: r.nom,
  contact: r.contact,
  typeService: r.type_service,
  tarif: parseFloat(r.tarif),
});

const toService = (r) => ({
  id: Number(r.id),
  prestataireId: Number(r.prestataire_id),
  nom: r.nom,
  description: r.description,
  tarif: parseFloat(r.tarif),
});

const fail = (res, status, message) =>
  res.status(status).json({ success: false, message });

const parseId = (value) => {
  const n = Number(value);
  return Number.isInteger(n) && n > 0 ? n : null;
};

const wrap = (fn) => (req, res, next) => fn(req, res, next).catch(next);

const isNonEmpty = (v) => typeof v === 'string' && v.trim().length > 0;
const isTarif = (v) => typeof v === 'number' && Number.isFinite(v) && v >= 0;

function validatePrestataire(b) {
  if (!b || typeof b !== 'object') return 'Corps de requête invalide.';
  for (const champ of ['nom', 'contact', 'typeService']) {
    if (!isNonEmpty(b[champ])) return `Le champ ${champ} est obligatoire.`;
  }
  if (!isTarif(b.tarif)) return 'Le tarif doit être un nombre positif ou nul.';
  return null;
}

function validateService(b) {
  if (!b || typeof b !== 'object') return 'Corps de requête invalide.';
  if (!isNonEmpty(b.nom)) return 'Le champ nom est obligatoire.';
  if (!isTarif(b.tarif)) return 'Le tarif doit être un nombre positif ou nul.';
  if (b.description != null && typeof b.description !== 'string') {
    return 'La description doit être une chaîne de caractères.';
  }
  return null;
}

async function prestataireExiste(id) {
  const { rowCount } = await pool.query(
    'select 1 from prestataires where id = $1',
    [id]
  );
  return rowCount > 0;
}

/* ---------- Prestataires ---------- */

app.post('/api/prestataires', wrap(async (req, res) => {
  const erreur = validatePrestataire(req.body);
  if (erreur) return fail(res, 400, erreur);

  const { nom, contact, typeService, tarif } = req.body;
  const { rows } = await pool.query(
    `insert into prestataires (nom, contact, type_service, tarif)
     values ($1, $2, $3, $4) returning *`,
    [nom.trim(), contact.trim(), typeService.trim(), tarif]
  );
  res.status(201).json(toPrestataire(rows[0]));
}));

app.get('/api/prestataires', wrap(async (req, res) => {
  const { rows } = await pool.query('select * from prestataires order by id');
  res.json(rows.map(toPrestataire));
}));

// Doit être déclarée AVANT /:id, sinon "recherche" serait lu comme un id.
app.get('/api/prestataires/recherche', wrap(async (req, res) => {
  const nom = isNonEmpty(req.query.nom) ? req.query.nom.trim() : null;
  const typeService = isNonEmpty(req.query.typeService)
    ? req.query.typeService.trim()
    : null;

  const { rows } = await pool.query(
    `select * from prestataires
     where ($1::text is null or nom ilike '%' || $1 || '%')
       and ($2::text is null or type_service ilike '%' || $2 || '%')
     order by id`,
    [nom, typeService]
  );
  res.json(rows.map(toPrestataire));
}));

app.get('/api/prestataires/:id', wrap(async (req, res) => {
  const id = parseId(req.params.id);
  if (!id) return fail(res, 400, 'Identifiant invalide.');

  const { rows } = await pool.query(
    'select * from prestataires where id = $1',
    [id]
  );
  if (rows.length === 0) return fail(res, 404, 'Prestataire introuvable.');

  const services = await pool.query(
    'select * from services_prestataire where prestataire_id = $1 order by id',
    [id]
  );
  res.json({ ...toPrestataire(rows[0]), services: services.rows.map(toService) });
}));

app.put('/api/prestataires/:id', wrap(async (req, res) => {
  const id = parseId(req.params.id);
  if (!id) return fail(res, 400, 'Identifiant invalide.');

  const erreur = validatePrestataire(req.body);
  if (erreur) return fail(res, 400, erreur);

  const { nom, contact, typeService, tarif } = req.body;
  const { rows } = await pool.query(
    `update prestataires
     set nom = $1, contact = $2, type_service = $3, tarif = $4, updated_at = now()
     where id = $5 returning *`,
    [nom.trim(), contact.trim(), typeService.trim(), tarif, id]
  );
  if (rows.length === 0) return fail(res, 404, 'Prestataire introuvable.');
  res.json(toPrestataire(rows[0]));
}));

app.delete('/api/prestataires/:id', wrap(async (req, res) => {
  const id = parseId(req.params.id);
  if (!id) return fail(res, 400, 'Identifiant invalide.');

  if (!(await prestataireExiste(id))) {
    return fail(res, 404, 'Prestataire introuvable.');
  }

  // Règle de gestion : pas de suppression si le prestataire est lié à un événement.
  const { rows } = await pool.query(
    'select count(*)::int as n from contrats_prestataire where prestataire_id = $1',
    [id]
  );
  if (rows[0].n > 0) {
    return fail(
      res,
      409,
      'Suppression impossible : le prestataire est associé à un événement ou à une réservation.'
    );
  }

  await pool.query('delete from prestataires where id = $1', [id]);
  res.status(204).end();
}));

/* ---------- Services ---------- */

app.post('/api/prestataires/:id/services', wrap(async (req, res) => {
  const id = parseId(req.params.id);
  if (!id) return fail(res, 400, 'Identifiant invalide.');

  const erreur = validateService(req.body);
  if (erreur) return fail(res, 400, erreur);

  if (!(await prestataireExiste(id))) {
    return fail(res, 404, 'Prestataire introuvable.');
  }

  const { nom, description, tarif } = req.body;
  const { rows } = await pool.query(
    `insert into services_prestataire (prestataire_id, nom, description, tarif)
     values ($1, $2, $3, $4) returning *`,
    [id, nom.trim(), description ?? null, tarif]
  );
  res.status(201).json(toService(rows[0]));
}));

app.get('/api/prestataires/:id/services', wrap(async (req, res) => {
  const id = parseId(req.params.id);
  if (!id) return fail(res, 400, 'Identifiant invalide.');

  if (!(await prestataireExiste(id))) {
    return fail(res, 404, 'Prestataire introuvable.');
  }

  const { rows } = await pool.query(
    'select * from services_prestataire where prestataire_id = $1 order by id',
    [id]
  );
  res.json(rows.map(toService));
}));

app.put('/api/prestataires/:id/services/:serviceId', wrap(async (req, res) => {
  const id = parseId(req.params.id);
  const serviceId = parseId(req.params.serviceId);
  if (!id || !serviceId) return fail(res, 400, 'Identifiant invalide.');

  const erreur = validateService(req.body);
  if (erreur) return fail(res, 400, erreur);

  const { nom, description, tarif } = req.body;
  const { rows } = await pool.query(
    `update services_prestataire
     set nom = $1, description = $2, tarif = $3
     where id = $4 and prestataire_id = $5 returning *`,
    [nom.trim(), description ?? null, tarif, serviceId, id]
  );
  if (rows.length === 0) return fail(res, 404, 'Service introuvable.');
  res.json(toService(rows[0]));
}));

app.delete('/api/prestataires/:id/services/:serviceId', wrap(async (req, res) => {
  const id = parseId(req.params.id);
  const serviceId = parseId(req.params.serviceId);
  if (!id || !serviceId) return fail(res, 400, 'Identifiant invalide.');

  const { rowCount } = await pool.query(
    'delete from services_prestataire where id = $1 and prestataire_id = $2',
    [serviceId, id]
  );
  if (rowCount === 0) return fail(res, 404, 'Service introuvable.');
  res.status(204).end();
}));

/* ---------- Gestion des erreurs ---------- */

app.use((req, res) => fail(res, 404, 'Route introuvable.'));

app.use((err, req, res, next) => {
  if (err.type === 'entity.parse.failed') {
    return fail(res, 400, 'JSON invalide.');
  }
  if (err.code === '23503') {
    // Filet de sécurité : violation de clé étrangère (ex. contrat créé entre-temps).
    return fail(res, 409, 'Opération impossible : le prestataire est référencé ailleurs.');
  }
  // On journalise l'erreur technique sans exposer de données sensibles au client.
  console.error(new Date().toISOString(), err.message);
  fail(res, 500, 'Erreur interne du serveur.');
});

const port = process.env.PORT || 5002;
app.listen(port, () => console.log(`Service prestataires sur le port ${port}`));
