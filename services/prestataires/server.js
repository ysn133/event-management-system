const express = require('express');
const pool = require('./db');

function createApp(dbPool = pool, clients = require('./integration-clients')) {
  const app = express();
  app.use((req, res, next) => {
    res.set('Access-Control-Allow-Origin', process.env.FRONTEND_ORIGIN || 'http://localhost:5173');
    res.set('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    res.set('Access-Control-Allow-Headers', 'Content-Type, Authorization');
    if (req.method === 'OPTIONS') return res.sendStatus(204);
    next();
  });
  app.get('/health', (req, res) => res.json({ status: 'ok' }));
  // Protect every API route before parsing a body or querying the database.
  app.use('/api', async (req, res, next) => {
    const authorization = req.headers.authorization || '';
    if (!/^Bearer\s+\S+$/i.test(authorization))
      return res.status(401).set('WWW-Authenticate', 'Bearer').json({ success: false, message: 'Authentification requise.' });
    try {
      req.authUser = await clients.authorize(authorization);
      if (!Number.isSafeInteger(req.authUser?.id) || req.authUser.id <= 0)
        throw new Error('Invalid identity response');
      req.authenticated = true;
      next();
    } catch (error) {
      const status = [401, 403].includes(error.status) ? error.status : 503;
      if (status === 401) res.set('WWW-Authenticate', 'Bearer');
      return res.status(status).json({ success: false, message: status === 503
        ? 'Authentification temporairement indisponible.' : 'Session invalide ou expirée.' });
    }
  });
  app.use(express.json({ limit: '100kb' }));

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

  const toUtilisation = (r) => ({
    id: Number(r.id),
    prestataireId: Number(r.prestataire_id),
    prestataireNom: r.prestataire_nom,
    evenementId: Number(r.evenement_id),
    evenementTitre: r.evenement_titre,
    prestation: r.prestation,
    montant: parseFloat(r.montant),
    datePrestation: r.date_prestation,
    statut: r.statut,
    ...(r.budget_sync_state ? { budgetSyncState: r.budget_sync_state, budgetSyncVersion: Number(r.budget_sync_version), pendingPayload: r.pending_payload } : {}),
  });

  const fail = (res, status, message) =>
    res.status(status).json({ success: false, message });

  const parseId = (value) => {
    const n = Number(value);
    return Number.isInteger(n) && n > 0 ? n : null;
  };

  const wrap = (fn) => (req, res, next) => fn(req, res, next).catch(next);

  const maxTarif = 9999999999.99;
  const isNonEmpty = (v) => typeof v === 'string' && v.trim().length > 0;
  const isTarif = (v) =>
    typeof v === 'number' && Number.isFinite(v) && v >= 0 && v <= maxTarif;

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
    const { rowCount } = await dbPool.query(
      'select 1 from prestataires where id = $1',
      [id]
    );
    return rowCount > 0;
  }

  app.post('/api/prestataires', wrap(async (req, res) => {
    const erreur = validatePrestataire(req.body);
    if (erreur) return fail(res, 400, erreur);

    const { nom, contact, typeService, tarif } = req.body;
    const { rows } = await dbPool.query(
      `insert into prestataires (nom, contact, type_service, tarif)
       values ($1, $2, $3, $4) returning *`,
      [nom.trim(), contact.trim(), typeService.trim(), tarif]
    );
    res.status(201).json(toPrestataire(rows[0]));
  }));

  app.get('/api/prestataires', wrap(async (req, res) => {
    const { rows } = await dbPool.query('select * from prestataires order by id');
    res.json(rows.map(toPrestataire));
  }));

  // Declare this route before /:id so "recherche" is not treated as an id.
  app.get('/api/prestataires/recherche', wrap(async (req, res) => {
    const nom = isNonEmpty(req.query.nom) ? req.query.nom.trim() : null;
    const typeService = isNonEmpty(req.query.typeService)
      ? req.query.typeService.trim()
      : null;

    const { rows } = await dbPool.query(
      `select * from prestataires
       where ($1::text is null or nom ilike '%' || $1 || '%')
         and ($2::text is null or type_service ilike '%' || $2 || '%')
       order by id`,
      [nom, typeService]
    );
    res.json(rows.map(toPrestataire));
  }));

  app.get('/api/prestataires/utilisations', wrap(async (req, res) => {
    const { rows } = await dbPool.query(
      `select cp.id, cp.prestataire_id, p.nom as prestataire_nom,
              cp.evenement_id, e.titre as evenement_titre,
              cp.prestation, cp.montant, cp.date_prestation, cp.statut,
              cp.budget_sync_state, cp.budget_sync_version, cp.pending_payload
       from contrats_prestataire cp
       join prestataires p on p.id = cp.prestataire_id
       join evenements e on e.id = cp.evenement_id
       order by cp.date_prestation, cp.id`
    );
    res.json(rows.map(toUtilisation));
  }));

  app.get('/api/prestataires/:id', wrap(async (req, res) => {
    const id = parseId(req.params.id);
    if (!id) return fail(res, 400, 'Identifiant invalide.');

    const { rows } = await dbPool.query(
      'select * from prestataires where id = $1',
      [id]
    );
    if (rows.length === 0) return fail(res, 404, 'Prestataire introuvable.');

    const services = await dbPool.query(
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
    const { rows } = await dbPool.query(
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

    // Keep providers that are still linked to an event or reservation.
    const { rows } = await dbPool.query(
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

    await dbPool.query('delete from prestataires where id = $1', [id]);
    res.status(204).end();
  }));

  app.post('/api/prestataires/:id/services', wrap(async (req, res) => {
    const id = parseId(req.params.id);
    if (!id) return fail(res, 400, 'Identifiant invalide.');

    const erreur = validateService(req.body);
    if (erreur) return fail(res, 400, erreur);

    if (!(await prestataireExiste(id))) {
      return fail(res, 404, 'Prestataire introuvable.');
    }

    const { nom, description, tarif } = req.body;
    const { rows } = await dbPool.query(
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

    const { rows } = await dbPool.query(
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
    const { rows } = await dbPool.query(
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

    const { rowCount } = await dbPool.query(
      'delete from services_prestataire where id = $1 and prestataire_id = $2',
      [serviceId, id]
    );
    if (rowCount === 0) return fail(res, 404, 'Service introuvable.');
    res.status(204).end();
  }));

  require('./contracts')(app, dbPool, clients);

  app.use((req, res) => fail(res, 404, 'Route introuvable.'));

  app.use((err, req, res, next) => {
    if (err.type === 'entity.too.large') return fail(res, 413, 'Corps de requête trop volumineux.');
    if (err.type === 'entity.parse.failed') {
      return fail(res, 400, 'JSON invalide.');
    }
    if ([400, 401, 403, 404, 409, 422, 503].includes(err.status)) return fail(res, err.status, err.message);
    if (err.code === '23503') {
      // A foreign-key constraint may be created by an integration service.
      return fail(res, 409, 'Opération impossible : le prestataire est référencé ailleurs.');
    }
    if (err.code === '22003') {
      return fail(res, 400, 'Valeur numérique hors limites.');
    }
    console.error(new Date().toISOString(), err.message);
    fail(res, 500, 'Erreur interne du serveur.');
  });

  return app;
}

if (require.main === module) {
  const port = process.env.PORT || 5002;
  createApp().listen(port, () => console.log(`Service prestataires sur le port ${port}`));
}

module.exports = createApp;
