// Durable pending payloads make an interrupted Budget call recoverable.
// Every retry reuses the contract id and version, so Budget never charges twice.
module.exports = function registerContracts(app, pool, clients) {
  const fail = (res, status, message, extra = {}) => res.status(status).json({ success: false, message, ...extra });
  const wrap = fn => (req, res, next) => fn(req, res).catch(next);
  const uuid = value => typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
  const id = value => Number.isSafeInteger(Number(value)) && Number(value) > 0 ? Number(value) : null;
  const valid = body => body && typeof body.prestation === 'string' && body.prestation.trim().length > 0 && body.prestation.trim().length <= 255
    && typeof body.montant === 'number' && Number.isFinite(body.montant) && body.montant > 0 && body.montant <= 9999999999.99
    && Math.abs(body.montant * 100 - Math.round(body.montant * 100)) < 0.0001
    && typeof body.datePrestation === 'string' && Number.isFinite(Date.parse(body.datePrestation))
    && ['EN_ATTENTE', 'CONFIRME', 'TERMINE', 'ANNULE'].includes(body.statut);
  const payloadOf = body => ({ prestation: body.prestation.trim(), montant: body.montant, datePrestation: new Date(body.datePrestation).toISOString(), statut: body.statut });
  const toContract = row => ({ id: Number(row.id), prestataireId: Number(row.prestataire_id), evenementId: Number(row.evenement_id),
    prestation: row.prestation, montant: Number(row.montant), datePrestation: row.date_prestation, statut: row.statut,
    budgetSyncState: row.budget_sync_state, budgetSyncVersion: Number(row.budget_sync_version) });
  async function transaction(fn) {
    const db = await pool.connect();
    try { await db.query('BEGIN'); const result = await fn(db); await db.query('COMMIT'); return result; }
    catch (error) { await db.query('ROLLBACK'); throw error; }
    finally { db.release(); }
  }
  async function authenticate(req, res) {
    if (req.authenticated) return true;
    if (!req.headers.authorization?.startsWith('Bearer ')) { fail(res, 401, 'Connectez-vous pour gérer les contrats.'); return false; }
    try { await clients.authorize(req.headers.authorization); return true; }
    catch (error) { fail(res, error.status || 503, error.status === 401 ? 'Session expirée. Reconnectez-vous.' : error.message); return false; }
  }
  async function synchronize(contractId, req, res, created = false) {
    let attemptedVersion;
    try {
      const result = await transaction(async db => {
        const { rows } = await db.query('select * from contrats_prestataire where id=$1 for update', [contractId]);
        const contract = rows[0];
        if (!contract) return null;
        if (!contract.pending_payload) return contract;
        const payload = contract.pending_payload;
        attemptedVersion = contract.budget_sync_version;
        await clients.budget(contract, payload, req.headers.authorization);
        const saved = await db.query(`update contrats_prestataire set prestation=$2,montant=$3,date_prestation=$4,statut=$5,
          budget_sync_state='SYNCED',pending_payload=null where id=$1 returning *`,
        [contractId, payload.prestation, payload.montant, payload.datePrestation, payload.statut]);
        return saved.rows[0];
      });
      if (!result) return fail(res, 404, 'Contrat introuvable.');
      return res.status(created ? 201 : 200).json(toContract(result));
    } catch (error) {
      if (error.definitive) {
        await pool.query("update contrats_prestataire set budget_sync_state='BLOCKED' where id=$1 and budget_sync_state='PENDING' and budget_sync_version=$2", [contractId, attemptedVersion]);
      }
      // A timeout or database failure can happen after Budget committed. Never
      // erase the pending record: the same version must be retried.
      return fail(res, error.status || 503, error.message || 'Synchronisation interrompue.', { contractId, pending: true });
    }
  }

  app.get('/api/contrats-prestataire/evenement/:eventId', wrap(async (req, res) => {
    if (!(await authenticate(req, res))) return;
    if (!id(req.params.eventId)) return fail(res, 400, 'Identifiant invalide.');
    const result = await pool.query('select * from contrats_prestataire where evenement_id=$1 order by id', [Number(req.params.eventId)]);
    // Include pending proposals: their existence must never be mistaken for a deleted source.
    res.json(result.rows.map(toContract));
  }));

  app.post('/api/contrats-prestataire', wrap(async (req, res) => {
    if (!(await authenticate(req, res))) return;
    const body = req.body;
    if (!valid(body) || !id(body.prestataireId) || !id(body.evenementId) || !uuid(body.requestId) || body.statut === 'ANNULE')
      return fail(res, 400, 'Prestataire, prestation, montant positif, date, statut et identifiant de demande sont obligatoires.');
    await clients.event(body.evenementId, req.headers.authorization);
    const payload = payloadOf(body);
    const contract = await transaction(async db => {
      const existing = await db.query('select * from contrats_prestataire where request_id=$1 for update', [body.requestId]);
      if (existing.rows[0]) return existing.rows[0];
      const provider = await db.query('select id from prestataires where id=$1', [body.prestataireId]);
      if (!provider.rows.length) { const error = new Error('Prestataire introuvable.'); error.status=404; throw error; }
      const result = await db.query(`insert into contrats_prestataire
        (prestataire_id,evenement_id,prestation,montant,date_prestation,statut,request_id,budget_sync_state,budget_sync_version,pending_payload)
        values ($1,$2,$3,$4,$5,$6,$7,'PENDING',1,$8::jsonb)
        on conflict (request_id) where request_id is not null do update set request_id=excluded.request_id returning *`,
      [body.prestataireId, body.evenementId, payload.prestation, payload.montant, payload.datePrestation, 'EN_ATTENTE', body.requestId, JSON.stringify(payload)]);
      return result.rows[0];
    });
    const original = contract.pending_payload || payloadOf({ prestation: contract.prestation, montant: Number(contract.montant), datePrestation: contract.date_prestation, statut: contract.statut });
    if (Number(contract.prestataire_id)!==body.prestataireId || Number(contract.evenement_id)!==body.evenementId || JSON.stringify(payloadOf(original))!==JSON.stringify(payload))
      return fail(res, 409, 'Cette demande a déjà été utilisée pour un autre contrat.');
    return synchronize(contract.id, req, res, true);
  }));

  app.put('/api/contrats-prestataire/:id', wrap(async (req, res) => {
    if (!(await authenticate(req, res))) return;
    if (!id(req.params.id) || !valid(req.body) || !Number.isSafeInteger(req.body.version)) return fail(res, 400, 'Données de contrat invalides.');
    const staged = await transaction(async db => {
      const result = await db.query('select * from contrats_prestataire where id=$1 for update', [req.params.id]);
      const contract = result.rows[0];
      if (!contract) return { error: 404, message: 'Contrat introuvable.' };
      if (contract.budget_sync_state==='PENDING') return { error: 409, message: 'Réessayez la synchronisation en attente avant de modifier le contrat.' };
      if (Number(contract.budget_sync_version)!==req.body.version) return { error: 409, message: 'Le contrat a changé. Actualisez la liste.' };
      await db.query(`update contrats_prestataire set budget_sync_state='PENDING',budget_sync_version=budget_sync_version+1,
        pending_payload=$2::jsonb where id=$1`, [contract.id, JSON.stringify(payloadOf(req.body))]);
      return contract;
    });
    if (staged.error) return fail(res, staged.error, staged.message);
    return synchronize(staged.id, req, res);
  }));

  app.post('/api/contrats-prestataire/:id/synchroniser', wrap(async (req, res) => {
    if (!(await authenticate(req, res))) return;
    if (!id(req.params.id)) return fail(res, 400, 'Identifiant invalide.');
    return synchronize(req.params.id, req, res);
  }));
};
