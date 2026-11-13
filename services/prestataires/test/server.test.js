const assert = require('node:assert/strict');
const { test } = require('node:test');
const createApp = require('../server');

const providerRow = {
  id: '12',
  nom: 'Traiteur Atlas',
  contact: 'atlas@example.test',
  type_service: 'TRAITEUR',
  tarif: '1200.50',
};

const serviceRow = {
  id: '34',
  prestataire_id: '12',
  nom: 'Buffet',
  description: 'Service complet',
  tarif: '650.00',
};

async function startServer(t, dbPool) {
  const server = createApp(dbPool).listen(0);
  await new Promise((resolve) => server.once('listening', resolve));
  t.after(() => new Promise((resolve, reject) => {
    server.close((error) => error ? reject(error) : resolve());
  }));
  return `http://127.0.0.1:${server.address().port}`;
}

async function send(baseUrl, path, method = 'GET', body) {
  return fetch(`${baseUrl}${path}`, {
    method,
    headers: body === undefined ? {} : { 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

test('rejects invalid provider input without querying the database', async (t) => {
  let queryCount = 0;
  const baseUrl = await startServer(t, {
    query: async () => {
      queryCount += 1;
      return { rows: [] };
    },
  });

  const response = await fetch(`${baseUrl}/api/prestataires`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ nom: ' ', tarif: -1 }),
  });

  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), {
    success: false,
    message: 'Le champ nom est obligatoire.',
  });
  assert.equal(queryCount, 0);
});

test('rejects invalid ids before querying the database', async (t) => {
  let queryCount = 0;
  const baseUrl = await startServer(t, {
    query: async () => {
      queryCount += 1;
      return { rows: [] };
    },
  });

  const response = await fetch(`${baseUrl}/api/prestataires/not-an-id`);

  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), {
    success: false,
    message: 'Identifiant invalide.',
  });
  assert.equal(queryCount, 0);
});

test('search route precedes the id route and maps PostgreSQL numeric values', async (t) => {
  const calls = [];
  const baseUrl = await startServer(t, {
    query: async (sql, params) => {
      calls.push({ sql, params });
      return {
        rows: [{
          id: '7',
          nom: 'Traiteur Atlas',
          contact: 'atlas@example.test',
          type_service: 'TRAITEUR',
          tarif: '1200.50',
        }],
      };
    },
  });

  const response = await fetch(`${baseUrl}/api/prestataires/recherche?nom=Atlas`);

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), [{
    id: 7,
    nom: 'Traiteur Atlas',
    contact: 'atlas@example.test',
    typeService: 'TRAITEUR',
    tarif: 1200.5,
  }]);
  assert.deepEqual(calls[0].params, ['Atlas', null]);
});

test('refuses to delete a provider referenced by an event or reservation', async (t) => {
  let callCount = 0;
  const baseUrl = await startServer(t, {
    query: async () => {
      callCount += 1;
      if (callCount === 1) return { rowCount: 1, rows: [] };
      return { rows: [{ n: 1 }] };
    },
  });

  const response = await fetch(`${baseUrl}/api/prestataires/3`, {
    method: 'DELETE',
  });

  assert.equal(response.status, 409);
  assert.deepEqual(await response.json(), {
    success: false,
    message: 'Suppression impossible : le prestataire est associé à un événement ou à une réservation.',
  });
  assert.equal(callCount, 2);
});

test('provider detail returns its services with a nullable description', async (t) => {
  let callCount = 0;
  const baseUrl = await startServer(t, {
    query: async () => {
      callCount += 1;
      if (callCount === 1) {
        return {
          rows: [{
            id: '2',
            nom: 'Salle Atlas',
            contact: 'atlas@example.test',
            type_service: 'SALLE',
            tarif: '2500.00',
          }],
        };
      }
      return {
        rows: [{
          id: '9',
          prestataire_id: '2',
          nom: 'Mise en place',
          description: null,
          tarif: '300.00',
        }],
      };
    },
  });

  const response = await fetch(`${baseUrl}/api/prestataires/2`);

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    id: 2,
    nom: 'Salle Atlas',
    contact: 'atlas@example.test',
    typeService: 'SALLE',
    tarif: 2500,
    services: [{
      id: 9,
      prestataireId: 2,
      nom: 'Mise en place',
      description: null,
      tarif: 300,
    }],
  });
});

test('creates a valid provider and trims required string fields', async (t) => {
  let call;
  const baseUrl = await startServer(t, {
    query: async (sql, params) => {
      call = { sql, params };
      return { rows: [providerRow] };
    },
  });

  const response = await send(baseUrl, '/api/prestataires', 'POST', {
    nom: '  Traiteur Atlas ',
    contact: ' atlas@example.test ',
    typeService: ' TRAITEUR ',
    tarif: 1200.5,
  });

  assert.equal(response.status, 201);
  assert.deepEqual(await response.json(), {
    id: 12,
    nom: 'Traiteur Atlas',
    contact: 'atlas@example.test',
    typeService: 'TRAITEUR',
    tarif: 1200.5,
  });
  assert.deepEqual(call.params, ['Traiteur Atlas', 'atlas@example.test', 'TRAITEUR', 1200.5]);
  assert.match(call.sql, /insert into prestataires/i);
});

test('requires contact and type when creating a provider', async (t) => {
  let calls = 0;
  const baseUrl = await startServer(t, {
    query: async () => {
      calls += 1;
      return { rows: [] };
    },
  });

  for (const body of [
    { nom: 'Traiteur', typeService: 'TRAITEUR', tarif: 10 },
    { nom: 'Traiteur', contact: 'contact', tarif: 10 },
  ]) {
    const response = await send(baseUrl, '/api/prestataires', 'POST', body);
    assert.equal(response.status, 400);
  }
  assert.equal(calls, 0);
});

test('rejects negative and non-numeric provider tariffs', async (t) => {
  let calls = 0;
  const baseUrl = await startServer(t, {
    query: async () => {
      calls += 1;
      return { rows: [] };
    },
  });

  for (const tarif of [-1, '12', null]) {
    const response = await send(baseUrl, '/api/prestataires', 'POST', {
      nom: 'Traiteur',
      contact: 'contact',
      typeService: 'TRAITEUR',
      tarif,
    });
    assert.equal(response.status, 400);
  }
  assert.equal(calls, 0);
});

test('rejects a provider tariff beyond the PostgreSQL NUMERIC(12,2) limit', async (t) => {
  let calls = 0;
  const baseUrl = await startServer(t, {
    query: async () => {
      calls += 1;
      return { rows: [] };
    },
  });

  const response = await send(baseUrl, '/api/prestataires', 'POST', {
    nom: 'Traiteur',
    contact: 'contact',
    typeService: 'TRAITEUR',
    tarif: 10000000000,
  });

  assert.equal(response.status, 400);
  assert.equal(calls, 0);
});

test('accepts zero and the maximum PostgreSQL NUMERIC(12,2) tariff', async (t) => {
  const inserted = [];
  const baseUrl = await startServer(t, {
    query: async (sql, params) => {
      inserted.push(params[3]);
      return { rows: [{ ...providerRow, tarif: String(params[3]) }] };
    },
  });

  for (const tarif of [0, 9999999999.99]) {
    const response = await send(baseUrl, '/api/prestataires', 'POST', {
      nom: 'Traiteur',
      contact: 'contact',
      typeService: 'TRAITEUR',
      tarif,
    });
    assert.equal(response.status, 201);
    assert.equal((await response.json()).tarif, tarif);
  }
  assert.deepEqual(inserted, [0, 9999999999.99]);
});

test('lists providers and maps database rows to the API representation', async (t) => {
  const baseUrl = await startServer(t, {
    query: async () => ({ rows: [providerRow] }),
  });

  const response = await send(baseUrl, '/api/prestataires');

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), [{
    id: 12,
    nom: 'Traiteur Atlas',
    contact: 'atlas@example.test',
    typeService: 'TRAITEUR',
    tarif: 1200.5,
  }]);
});

test('search filters by name and service type with parameterized values', async (t) => {
  let call;
  const baseUrl = await startServer(t, {
    query: async (sql, params) => {
      call = { sql, params };
      return { rows: [providerRow] };
    },
  });

  const response = await send(
    baseUrl,
    '/api/prestataires/recherche?nom=%20Atlas%20&typeService=TRAITEUR'
  );

  assert.equal(response.status, 200);
  assert.deepEqual(call.params, ['Atlas', 'TRAITEUR']);
  assert.match(call.sql, /ilike/i);
  assert.deepEqual((await response.json()).map((row) => row.id), [12]);
});

test('returns 404 when the requested provider does not exist', async (t) => {
  const baseUrl = await startServer(t, {
    query: async () => ({ rows: [] }),
  });

  const response = await send(baseUrl, '/api/prestataires/404');

  assert.equal(response.status, 404);
  assert.deepEqual(await response.json(), {
    success: false,
    message: 'Prestataire introuvable.',
  });
});

test('updates a provider and includes the id in the parameterized query', async (t) => {
  let call;
  const baseUrl = await startServer(t, {
    query: async (sql, params) => {
      call = { sql, params };
      return { rows: [providerRow] };
    },
  });

  const response = await send(baseUrl, '/api/prestataires/12', 'PUT', {
    nom: 'Traiteur Atlas',
    contact: 'atlas@example.test',
    typeService: 'TRAITEUR',
    tarif: 1200.5,
  });

  assert.equal(response.status, 200);
  assert.equal((await response.json()).id, 12);
  assert.deepEqual(call.params, ['Traiteur Atlas', 'atlas@example.test', 'TRAITEUR', 1200.5, 12]);
  assert.match(call.sql, /update prestataires/i);
});

test('returns 404 when updating a provider that does not exist', async (t) => {
  const baseUrl = await startServer(t, {
    query: async () => ({ rows: [] }),
  });

  const response = await send(baseUrl, '/api/prestataires/404', 'PUT', {
    nom: 'Traiteur',
    contact: 'contact',
    typeService: 'TRAITEUR',
    tarif: 0,
  });

  assert.equal(response.status, 404);
});

test('returns 404 when a provider to delete does not exist', async (t) => {
  let calls = 0;
  const baseUrl = await startServer(t, {
    query: async () => {
      calls += 1;
      return { rowCount: 0, rows: [] };
    },
  });

  const response = await send(baseUrl, '/api/prestataires/404', 'DELETE');

  assert.equal(response.status, 404);
  assert.equal(calls, 1);
});

test('deletes an unreferenced provider with an empty 204 response', async (t) => {
  let calls = 0;
  const baseUrl = await startServer(t, {
    query: async () => {
      calls += 1;
      if (calls === 1) return { rowCount: 1, rows: [] };
      if (calls === 2) return { rows: [{ n: 0 }] };
      return { rowCount: 1, rows: [] };
    },
  });

  const response = await send(baseUrl, '/api/prestataires/12', 'DELETE');

  assert.equal(response.status, 204);
  assert.equal(await response.text(), '');
  assert.equal(calls, 3);
});

test('creates a service for an existing provider', async (t) => {
  let calls = 0;
  let insertParams;
  const baseUrl = await startServer(t, {
    query: async (sql, params) => {
      calls += 1;
      if (calls === 1) return { rowCount: 1, rows: [] };
      insertParams = params;
      return { rows: [serviceRow] };
    },
  });

  const response = await send(baseUrl, '/api/prestataires/12/services', 'POST', {
    nom: 'Buffet',
    description: 'Service complet',
    tarif: 650,
  });

  assert.equal(response.status, 201);
  assert.deepEqual(await response.json(), {
    id: 34,
    prestataireId: 12,
    nom: 'Buffet',
    description: 'Service complet',
    tarif: 650,
  });
  assert.deepEqual(insertParams, [12, 'Buffet', 'Service complet', 650]);
});

test('stores an omitted service description as null', async (t) => {
  let insertParams;
  const baseUrl = await startServer(t, {
    query: async (sql, params) => {
      if (/^select 1/i.test(sql.trim())) return { rowCount: 1, rows: [] };
      insertParams = params;
      return { rows: [{ ...serviceRow, description: null }] };
    },
  });

  const response = await send(baseUrl, '/api/prestataires/12/services', 'POST', {
    nom: 'Installation',
    tarif: 0,
  });

  assert.equal(response.status, 201);
  assert.equal((await response.json()).description, null);
  assert.equal(insertParams[2], null);
});

test('rejects a service with a negative tariff without querying the database', async (t) => {
  let calls = 0;
  const baseUrl = await startServer(t, {
    query: async () => {
      calls += 1;
      return { rows: [] };
    },
  });

  const response = await send(baseUrl, '/api/prestataires/12/services', 'POST', {
    nom: 'Buffet',
    tarif: -1,
  });

  assert.equal(response.status, 400);
  assert.equal(calls, 0);
});

test('rejects a service tariff beyond the PostgreSQL NUMERIC(12,2) limit', async (t) => {
  let calls = 0;
  const baseUrl = await startServer(t, {
    query: async () => {
      calls += 1;
      return { rowCount: 1, rows: [] };
    },
  });

  const response = await send(baseUrl, '/api/prestataires/12/services', 'POST', {
    nom: 'Buffet',
    tarif: 10000000000,
  });

  assert.equal(response.status, 400);
  assert.equal(calls, 0);
});

test('does not create a service for a missing provider', async (t) => {
  let calls = 0;
  const baseUrl = await startServer(t, {
    query: async () => {
      calls += 1;
      return { rowCount: 0, rows: [] };
    },
  });

  const response = await send(baseUrl, '/api/prestataires/404/services', 'POST', {
    nom: 'Buffet',
    tarif: 100,
  });

  assert.equal(response.status, 404);
  assert.equal(calls, 1);
});

test('lists services for an existing provider', async (t) => {
  let calls = 0;
  const baseUrl = await startServer(t, {
    query: async () => {
      calls += 1;
      return calls === 1
        ? { rowCount: 1, rows: [] }
        : { rows: [serviceRow] };
    },
  });

  const response = await send(baseUrl, '/api/prestataires/12/services');

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), [{
    id: 34,
    prestataireId: 12,
    nom: 'Buffet',
    description: 'Service complet',
    tarif: 650,
  }]);
  assert.equal(calls, 2);
});

test('returns 404 when listing services for a missing provider', async (t) => {
  const baseUrl = await startServer(t, {
    query: async () => ({ rowCount: 0, rows: [] }),
  });

  const response = await send(baseUrl, '/api/prestataires/404/services');

  assert.equal(response.status, 404);
});

test('updates a service belonging to the provider', async (t) => {
  let call;
  const baseUrl = await startServer(t, {
    query: async (sql, params) => {
      call = { sql, params };
      return { rows: [serviceRow] };
    },
  });

  const response = await send(baseUrl, '/api/prestataires/12/services/34', 'PUT', {
    nom: 'Buffet premium',
    description: null,
    tarif: 900,
  });

  assert.equal(response.status, 200);
  assert.deepEqual(call.params, ['Buffet premium', null, 900, 34, 12]);
  assert.match(call.sql, /where id = \$4 and prestataire_id = \$5/i);
});

test('returns 404 when updating a service not owned by the provider', async (t) => {
  const baseUrl = await startServer(t, {
    query: async () => ({ rows: [] }),
  });

  const response = await send(baseUrl, '/api/prestataires/12/services/404', 'PUT', {
    nom: 'Buffet',
    tarif: 100,
  });

  assert.equal(response.status, 404);
});

test('deletes a service and returns 204', async (t) => {
  let params;
  const baseUrl = await startServer(t, {
    query: async (sql, values) => {
      params = values;
      return { rowCount: 1, rows: [] };
    },
  });

  const response = await send(baseUrl, '/api/prestataires/12/services/34', 'DELETE');

  assert.equal(response.status, 204);
  assert.equal(await response.text(), '');
  assert.deepEqual(params, [34, 12]);
});

test('returns 404 when deleting a missing service', async (t) => {
  const baseUrl = await startServer(t, {
    query: async () => ({ rowCount: 0, rows: [] }),
  });

  const response = await send(baseUrl, '/api/prestataires/12/services/404', 'DELETE');

  assert.equal(response.status, 404);
});

test('rejects malformed JSON with a controlled 400 response', async (t) => {
  const baseUrl = await startServer(t, { query: async () => ({ rows: [] }) });
  const response = await fetch(`${baseUrl}/api/prestataires`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: '{"nom":',
  });

  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), {
    success: false,
    message: 'JSON invalide.',
  });
});

test('returns a consistent JSON 404 for an unknown route', async (t) => {
  const baseUrl = await startServer(t, { query: async () => ({ rows: [] }) });

  const response = await send(baseUrl, '/api/unknown');

  assert.equal(response.status, 404);
  assert.deepEqual(await response.json(), {
    success: false,
    message: 'Route introuvable.',
  });
});

test('maps foreign-key violations to a conflict response', async (t) => {
  const baseUrl = await startServer(t, {
    query: async () => {
      const error = new Error('foreign key violation');
      error.code = '23503';
      throw error;
    },
  });
  const originalError = console.error;
  console.error = () => {};
  t.after(() => { console.error = originalError; });

  const response = await send(baseUrl, '/api/prestataires', 'POST', {
    nom: 'Traiteur',
    contact: 'contact',
    typeService: 'TRAITEUR',
    tarif: 10,
  });

  assert.equal(response.status, 409);
  assert.equal((await response.json()).success, false);
});

test('maps PostgreSQL numeric overflow to a controlled 400 response', async (t) => {
  const baseUrl = await startServer(t, {
    query: async () => {
      const error = new Error('numeric field overflow');
      error.code = '22003';
      throw error;
    },
  });
  const originalError = console.error;
  console.error = () => {};
  t.after(() => { console.error = originalError; });

  const response = await send(baseUrl, '/api/prestataires', 'POST', {
    nom: 'Traiteur',
    contact: 'contact',
    typeService: 'TRAITEUR',
    tarif: 100,
  });

  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), {
    success: false,
    message: 'Valeur numérique hors limites.',
  });
});

test('returns a generic 500 response for an unexpected database error', async (t) => {
  const baseUrl = await startServer(t, {
    query: async () => {
      throw new Error('database connection detail');
    },
  });
  const originalError = console.error;
  console.error = () => {};
  t.after(() => { console.error = originalError; });

  const response = await send(baseUrl, '/api/prestataires');

  assert.equal(response.status, 500);
  assert.deepEqual(await response.json(), {
    success: false,
    message: 'Erreur interne du serveur.',
  });
});
